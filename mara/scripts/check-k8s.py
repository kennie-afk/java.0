#!/usr/bin/env python3
"""Static checks on mara/k8s: the mistakes that only show up when a pod refuses to start, a policy silently does nothing,
or a variable a service requires is never set. Not a substitute for applying the manifests to a real cluster.
Exit status 1 when any check fails.
"""
import glob
import os
import re
import subprocess
import sys

import yaml

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
errors = []


def fail(msg):
    errors.append(msg)


# the committed files are exactly what the generator writes
r = subprocess.run([sys.executable, os.path.join(ROOT, "scripts", "gen-k8s.py"), "--check"], capture_output=True, text=True)
if r.returncode != 0:
    fail(r.stdout.strip() or r.stderr.strip())

docs = []
for path in sorted(glob.glob(os.path.join(ROOT, "k8s", "*.yaml"))):
    if path.endswith("02-secret.example.yaml") or path.endswith("kustomization.yaml"):
        continue
    for d in yaml.safe_load_all(open(path)):
        if d:
            d["_file"] = os.path.basename(path)
            docs.append(d)
secret = next(yaml.safe_load_all(open(os.path.join(ROOT, "k8s", "02-secret.example.yaml"))))
secret_keys = set(secret["stringData"])
config = next(d for d in docs if d["kind"] == "ConfigMap")
config_keys = set(config["data"])
by_kind = lambda k: [d for d in docs if d["kind"] == k]
name = lambda d: f'{d["kind"]}/{d["metadata"]["name"]}'

ns = by_kind("Namespace")[0]["metadata"]["name"]
for d in docs:
    if d["kind"] != "Namespace" and d["metadata"].get("namespace") != ns:
        fail(f"{name(d)}: namespace is not {ns}")
if by_kind("Namespace")[0]["metadata"]["labels"].get("pod-security.kubernetes.io/enforce") != "restricted":
    fail("Namespace does not enforce the restricted pod security profile")


def dockerfile_user(rel):
    for line in open(os.path.join(ROOT, rel)).read().splitlines()[::-1]:
        m = re.match(r"USER\s+(\d+)(?::(\d+))?\s*$", line.strip())
        if m:
            return int(m.group(1))
    return None


image_uid = {"identity-service": dockerfile_user("services/identity-service/Dockerfile"),
             "sync-service": dockerfile_user("services/sync-service/Dockerfile"),
             "core-service": dockerfile_user("services/core-service/Dockerfile"),
             "terminal": dockerfile_user("apps/terminal/Dockerfile")}
for k, v in image_uid.items():
    if v is None:
        fail(f"{k}: the Dockerfile's last USER is not numeric, so Kubernetes cannot verify it is non-root")

workloads = by_kind("Deployment") + by_kind("StatefulSet")
pod_labels = {}
used_config_keys = set()
secret_users = {}      # secret key -> set of workload names that read it
env_by_workload = {}   # workload name -> env var names provided
for w in workloads:
    spec = w["spec"]["template"]["spec"]
    labels = w["spec"]["template"]["metadata"]["labels"]
    wname = w["metadata"]["name"]
    pod_labels[name(w)] = labels
    sel = w["spec"]["selector"]["matchLabels"]
    if any(labels.get(k) != v for k, v in sel.items()):
        fail(f"{name(w)}: selector does not match the pod labels")
    ps = spec.get("securityContext", {})
    if not ps.get("runAsNonRoot") or not isinstance(ps.get("runAsUser"), int) or ps.get("runAsUser") == 0:
        fail(f"{name(w)}: pod must set runAsNonRoot and a numeric non-zero runAsUser")
    if ps.get("seccompProfile", {}).get("type") != "RuntimeDefault":
        fail(f"{name(w)}: seccompProfile must be RuntimeDefault")
    if spec.get("automountServiceAccountToken") is not False:
        fail(f"{name(w)}: automountServiceAccountToken must be false")
    if wname in image_uid and image_uid[wname] is not None and ps.get("runAsUser") != image_uid[wname]:
        fail(f"{name(w)}: runAsUser {ps.get('runAsUser')} differs from the Dockerfile's USER {image_uid[wname]}")
    env_by_workload[wname] = set()
    for c in [dict(i, _init=True) for i in spec.get("initContainers", [])] + spec["containers"]:
        init = c.get("_init", False)
        who = f'{name(w)}/{c["name"]}'
        img = c["image"]
        if ":" not in img.split("/")[-1] or img.endswith(":latest"):
            fail(f"{who}: image {img} needs a pinned tag, not latest or none")
        r = c.get("resources", {})
        for part in ("requests", "limits"):
            for res in ("cpu", "memory"):
                if res not in r.get(part, {}):
                    fail(f"{who}: resources.{part}.{res} missing")
        for probe in ("startupProbe", "readinessProbe", "livenessProbe"):
            if probe not in c and not init:
                fail(f"{who}: {probe} missing")
        sc = c.get("securityContext", {})
        if sc.get("allowPrivilegeEscalation") is not False:
            fail(f"{who}: allowPrivilegeEscalation must be false")
        if "ALL" not in sc.get("capabilities", {}).get("drop", []):
            fail(f"{who}: capabilities must drop ALL")
        port_names = {p["name"] for p in c.get("ports", []) if "name" in p}
        for probe in ("startupProbe", "readinessProbe", "livenessProbe"):
            p = c.get(probe, {}).get("httpGet")
            if p and isinstance(p["port"], str) and p["port"] not in port_names:
                fail(f"{who}: {probe} names port {p['port']} the container does not declare")
        for e in c.get("env", []):
            env_by_workload[wname].add(e["name"])
            vf = e.get("valueFrom", {})
            if "configMapKeyRef" in vf:
                used_config_keys.add(vf["configMapKeyRef"]["key"])
                if vf["configMapKeyRef"]["key"] not in config_keys:
                    fail(f"{who}: ConfigMap key {vf['configMapKeyRef']['key']} does not exist")
            if "secretKeyRef" in vf:
                k = vf["secretKeyRef"]["key"]
                secret_users.setdefault(k, set()).add(wname)
                if k not in secret_keys:
                    fail(f"{who}: Secret key {k} is not in the example Secret")
        if sc.get("readOnlyRootFilesystem") and not init:
            mounted = {m["mountPath"] for m in c.get("volumeMounts", [])}
            if not any(p in mounted for p in ("/tmp", "/var/lib/postgresql/data")):
                fail(f"{who}: read-only root filesystem with no writable /tmp")

# a ConfigMap key nothing reads is an inert setting: someone will edit it and nothing will change
for k in sorted(config_keys - used_config_keys):
    fail(f"ConfigMap key {k} is not read by any workload")

# every variable a service's application.yml requires (a placeholder with no default) is provided to its pod
for svc in ("identity-service", "sync-service", "core-service"):
    text = open(os.path.join(ROOT, "services", svc, "src/main/resources/application.yml")).read()
    for m in re.finditer(r"\$\{([A-Z][A-Z0-9_]*)(:[^}]*)?\}", text):
        var, default = m.group(1), m.group(2)
        if default is None and var not in env_by_workload.get(svc, set()):
            fail(f"{svc}: application.yml requires {var} (no default) but the pod is not given it")

# credential isolation: each service reads only its own credential
allowed = {"MARA_SVC_SYNC_CREDENTIAL": {"identity-service", "sync-service"},
           "MARA_SVC_CORE_CREDENTIAL": {"identity-service", "core-service"},
           "MARA_BOOTSTRAP_CREDENTIAL": {"identity-service"}}
for key, who in allowed.items():
    extra = secret_users.get(key, set()) - who
    if extra:
        fail(f"Secret key {key} is read by {sorted(extra)}, which must not hold it")
for key in ("MARA_SVC_SYNC_CREDENTIAL", "MARA_SVC_CORE_CREDENTIAL"):
    if not secret_users.get(key):
        fail(f"Secret key {key} is read by no workload")
for w in workloads:
    for c in w["spec"]["template"]["spec"]["containers"]:
        for ef in c.get("envFrom", []):
            if "secretRef" in ef:
                fail(f"{name(w)}/{c['name']}: envFrom a whole Secret would hand every credential to this pod; list the keys")

required_secret = {"MARA_DB_OWNER_PASSWORD", "MARA_DB_APP_PASSWORD", "MARA_SVC_SYNC_CREDENTIAL", "MARA_SVC_CORE_CREDENTIAL"}
if not required_secret <= secret_keys:
    fail(f"example Secret lacks {sorted(required_secret - secret_keys)}")

services = {s["metadata"]["name"]: s for s in by_kind("Service")}
for s in services.values():
    sel = s["spec"]["selector"]
    if not any(all(l.get(k) == v for k, v in sel.items()) for l in pod_labels.values()):
        fail(f"{name(s)}: selector matches no workload")
workload_names = {(w["kind"], w["metadata"]["name"]) for w in workloads}
hpa_targets = set()
for h in by_kind("HorizontalPodAutoscaler"):
    t = h["spec"]["scaleTargetRef"]
    hpa_targets.add(t["name"])
    if (t["kind"], t["name"]) not in workload_names:
        fail(f"{name(h)}: targets {t['kind']}/{t['name']} which does not exist")
    if h["spec"]["minReplicas"] < 2:
        fail(f"{name(h)}: minReplicas below 2 leaves no redundancy")
pdb_names = set()
for p in by_kind("PodDisruptionBudget"):
    sel = p["spec"]["selector"]["matchLabels"]
    if not any(all(l.get(k) == v for k, v in sel.items()) for l in pod_labels.values()):
        fail(f"{name(p)}: selector matches no workload")
    pdb_names.add(p["metadata"]["name"])
for w in by_kind("Deployment"):
    n = w["metadata"]["name"]
    if n == "redis":
        continue   # a single ephemeral counter store: an autoscaler or a budget would only mislead
    if n not in hpa_targets:
        fail(f"{name(w)}: no HorizontalPodAutoscaler")
    if n not in pdb_names:
        fail(f"{name(w)}: no PodDisruptionBudget")
for ing in by_kind("Ingress"):
    for rule in ing["spec"]["rules"]:
        for path in rule["http"]["paths"]:
            svc = path["backend"]["service"]
            if svc["name"] != "terminal":
                fail(f"{name(ing)}: routes to {svc['name']}; only the terminal may be public")
            if svc["name"] not in services:
                fail(f"{name(ing)}: backend service {svc['name']} does not exist")
            elif svc["port"]["number"] not in [p["port"] for p in services[svc["name"]]["spec"]["ports"]]:
                fail(f"{name(ing)}: service {svc['name']} has no port {svc['port']['number']}")
            if path["path"].startswith("/actuator"):
                fail(f"{name(ing)}: exposes the actuator")
    for t in ing["spec"].get("tls", []):
        if not t.get("secretName"):
            fail(f"{name(ing)}: tls without a secretName")

policies = by_kind("NetworkPolicy")
if not any(p["spec"]["podSelector"] == {} and set(p["spec"]["policyTypes"]) >= {"Ingress", "Egress"}
           and not p["spec"].get("ingress") and not p["spec"].get("egress") for p in policies):
    fail("no default-deny NetworkPolicy for ingress and egress")
for key, labels in pod_labels.items():
    selected = [p for p in policies if p["spec"]["podSelector"].get("matchLabels")
                and all(labels.get(k) == v for k, v in p["spec"]["podSelector"]["matchLabels"].items())]
    if not selected:
        fail(f"{key}: no NetworkPolicy allows it any traffic")
for p in policies:
    for rule in p["spec"].get("egress", []) + p["spec"].get("ingress", []):
        for peer in rule.get("to", []) + rule.get("from", []):
            ml = peer.get("podSelector", {}).get("matchLabels")
            if ml and not any(all(l.get(k) == v for k, v in ml.items()) for l in pod_labels.values()):
                fail(f"{name(p)}: peer {ml} matches no workload")
# the rate limits trust the ingress-appended address only if nothing else can reach the terminal pods
trust = config["data"].get("MARA_RATELIMIT_TRUST_FORWARDED_FOR") == "true"
term_pol = next((p for p in policies if p["metadata"]["name"] == "terminal"), None)
if trust:
    froms = [peer for rule in (term_pol or {"spec": {}})["spec"].get("ingress", []) for peer in rule.get("from", [])]
    if not froms or any("namespaceSelector" not in peer or "podSelector" in peer for peer in froms):
        fail("MARA_RATELIMIT_TRUST_FORWARDED_FOR is true but the terminal NetworkPolicy admits more than the ingress controller")
# the services themselves must not be reachable from outside the namespace
for p in policies:
    if p["metadata"]["name"] in ("identity-service", "sync-service", "core-service"):
        for rule in p["spec"].get("ingress", []):
            if any("namespaceSelector" in peer or "ipBlock" in peer for peer in rule.get("from", [])):
                fail(f"{name(p)}: admits traffic from outside the namespace")

if errors:
    print("\n".join(f"FAIL {e}" for e in errors))
    sys.exit(1)
print(f"k8s checks passed: {len(docs)} objects, {len(workloads)} workloads, {len(policies)} network policies")
