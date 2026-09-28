#!/usr/bin/env python3
"""Checks that everything the services are told to connect to actually exists.

Written 2026-09-11, after the manifests were found to deploy 29 services that pointed
at `postgres.smartseason.svc.cluster.local`, `redis` and `kafka:9092` while the
repository created none of them. `kubectl apply -f k8s/` would have produced a
namespace of crash-looping pods.

The existing gates could not see it, and that is the interesting part rather than an
oversight: `check-k8s-scaling.py` asks whether a Deployment can grow and survive a
drain, and SmartRE's `check-k8s-env.py` asks whether every variable is provided and
every Secret key defined. Both answer their own question correctly. Nothing asked
whether the *value* of a variable names something that exists, so a complete set of
green checks described a set of manifests that could not start.

Three questions here, each of which caught something real:

  1. Every host named in the ConfigMap resolves to a Service in these manifests.
     This is the check that was missing entirely.
  2. Every Service selects a workload that exists and whose labels it matches. A
     Service with no backing pods is a DNS name that resolves and then refuses the
     connection, which reads like a network fault rather than a missing manifest.
  3. Every database named in a datasource URL is created by the init script. This
     caught `device-registry_db` and `telemetry-ingest_db`, invented by deriving the
     name from the service instead of reading the catalogue, while the real databases
     were `device_db` and `telemetry_db`.
"""
import glob
import os
import re
import sys

try:
    import yaml
except ImportError:
    print("pyyaml is required: pip install pyyaml")
    sys.exit(2)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "k8s")

services, workloads, configmaps, deployments = {}, {}, {}, {}

for path in sorted(glob.glob(os.path.join(ROOT, "*.yaml"))):
    for doc in yaml.safe_load_all(open(path)):
        if not doc:
            continue
        kind = doc.get("kind")
        name = doc.get("metadata", {}).get("name")
        if kind == "Service":
            services[name] = doc
        elif kind in ("Deployment", "StatefulSet"):
            workloads[name] = doc
            if kind == "Deployment":
                deployments[name] = doc
        elif kind == "ConfigMap":
            configmaps[name] = doc

problems = []
config = configmaps.get("smartseason-config", {}).get("data", {})


def service_name(host):
    """`redis.smartseason.svc.cluster.local:6379` -> `redis`."""
    return host.split(":")[0].split(".")[0].strip()


# 1. Every host the services are pointed at is a Service defined here.
for key in ("DB_HOST", "REDIS_HOST", "KAFKA_BOOTSTRAP_SERVERS"):
    value = config.get(key)
    if not value:
        problems.append(f"ConfigMap has no {key}; services fall back to a code default")
        continue
    target = service_name(value)
    if target not in services:
        problems.append(
            f"{key} points at '{target}', which no Service in k8s/ defines"
        )

# 2. Every Service actually selects something.
for name, svc in sorted(services.items()):
    selector = svc.get("spec", {}).get("selector") or {}
    if not selector:
        continue
    matched = False
    for wl in workloads.values():
        labels = (
            wl.get("spec", {})
            .get("template", {})
            .get("metadata", {})
            .get("labels", {})
        )
        if all(labels.get(k) == v for k, v in selector.items()):
            matched = True
            break
    if not matched:
        problems.append(f"Service {name} selects {selector}, which matches no workload")

# 3. Every database a service connects to is created at initialisation.
created = set()
init = configmaps.get("postgres-init", {}).get("data", {}).get("init-databases.sh", "")
for m in re.finditer(r"CREATE DATABASE (\w+);", init):
    created.add(m.group(1))

if not created:
    problems.append("no postgres-init ConfigMap; the databases are never created")
else:
    for name, dep in sorted(deployments.items()):
        for container in dep["spec"]["template"]["spec"].get("containers", []):
            for env in container.get("env", []):
                if env.get("name") != "SPRING_DATASOURCE_URL":
                    continue
                m = re.search(r"/(\w+)(?:\?|$)", env.get("value", ""))
                if m and m.group(1) not in created:
                    problems.append(
                        f"{name} connects to database '{m.group(1)}', "
                        f"which init-databases.sh never creates"
                    )

if problems:
    print("Data layer problems:\n")
    for p in problems:
        print(f"  - {p}")
    sys.exit(1)

print(
    f"ok: {len(services)} services all select a workload, "
    f"{len(created)} databases created, every configured host exists."
)
