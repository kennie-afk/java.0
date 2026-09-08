#!/usr/bin/env python3
"""Prove the k8s manifests can actually start every service before you apply them.

Three ways a deploy dies quietly, all of them checkable from the files alone:

  1. A service reads ${VAR} with no default. Spring refuses to start if the variable
     is absent, and the pod crash-loops with a placeholder-resolution error that names
     the property, not the missing env var.
  2. A manifest references a Secret or ConfigMap key that was never defined. The pod
     never starts at all — CreateContainerConfigError, before any application log.
  3. A ConfigMap defines a key no manifest wires up. Harmless to boot, but it makes the
     ConfigMap read as authoritative when it is inert, so changing it does nothing.
  4. A service reads ${VAR:http://localhost...} and no manifest overrides it. The pod
     starts perfectly and then behaves as though it were on a developer's laptop —
     emails whose links point at localhost, a gateway whose CORS allows an origin that
     does not exist. This is the quietest failure of the four, because nothing errors.

Run from the repo root. Exits non-zero on any finding, so CI can gate on it.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
K8S = ROOT / "k8s"

failures = []


def note(msg):
    failures.append(msg)
    print(f"  FAIL  {msg}")


def manifest_for(service):
    return K8S / f"{service}.yaml"


def required_env(application_yaml):
    """Env vars the service cannot start without: ${VAR} with no ':default'."""
    out = set()
    for m in re.finditer(r"\$\{([A-Za-z0-9_.]+)(:[^}]*)?\}", application_yaml.read_text()):
        name, default = m.group(1), m.group(2)
        if default is None and name.isupper():
            out.add(name)
    return out


def provided_env(manifest):
    return set(re.findall(r"- name: ([A-Z][A-Z0-9_]*)", manifest.read_text()))


print("1. mandatory env vars vs. what each Deployment provides")
for app_yaml in sorted(ROOT.glob("*/src/main/resources/application.yaml")):
    service = app_yaml.parts[len(ROOT.parts)] if app_yaml.is_absolute() else app_yaml.parts[0]
    service = app_yaml.relative_to(ROOT).parts[0]
    mf = manifest_for(service)
    if not mf.exists():
        note(f"{service}: no k8s manifest")
        continue
    missing = sorted(required_env(app_yaml) - provided_env(mf))
    if missing:
        note(f"{service}: manifest never sets {', '.join(missing)}")
    else:
        print(f"  ok    {service}")

print()
print("2. Secret and ConfigMap keys referenced vs. defined")


def defined_keys(path, block):
    text = path.read_text()
    body = text.split(f"{block}:", 1)[1]
    return set(re.findall(r"^  ([A-Z][A-Z0-9_]*):", body, re.M))


def referenced_keys(kind):
    keys = set()
    for f in K8S.glob("*.yaml"):
        for m in re.finditer(rf"{kind}:\s*\n\s*name: \S+\s*\n\s*key: (\S+)", f.read_text()):
            keys.add(m.group(1))
    return keys


secret_defined = defined_keys(K8S / "secret.yaml", "stringData")
config_defined = defined_keys(K8S / "configmap.yaml", "data")

for label, defined, referenced in (
    ("Secret smartre-secrets", secret_defined, referenced_keys("secretKeyRef")),
    ("ConfigMap smartre-config", config_defined, referenced_keys("configMapKeyRef")),
):
    undefined = sorted(referenced - defined)
    if undefined:
        note(f"{label}: referenced but not defined: {', '.join(undefined)}")
    else:
        print(f"  ok    {label}: every referenced key is defined")

print()
print("3. ConfigMap keys that no manifest reads (inert settings)")
inert = sorted(config_defined - referenced_keys("configMapKeyRef"))
if inert:
    note(
        "ConfigMap smartre-config: "
        + ", ".join(inert)
        + " are set but never wired into a container, so editing them changes nothing"
    )
else:
    print("  ok    every ConfigMap key reaches a container")

print()
print("4. localhost defaults that no manifest overrides")


def localhost_defaults(application_yaml):
    """${VAR:...localhost...} — fine locally, silently wrong in a cluster."""
    out = set()
    for m in re.finditer(r"\$\{([A-Z][A-Z0-9_]*):([^}]*)\}", application_yaml.read_text()):
        name, default = m.group(1), m.group(2)
        if "localhost" in default or "127.0.0.1" in default:
            out.add(name)
    return out


found_localhost = False
for app_yaml in sorted(ROOT.glob("*/src/main/resources/application.yaml")):
    service = app_yaml.relative_to(ROOT).parts[0]
    mf = manifest_for(service)
    if not mf.exists():
        continue
    leaking = sorted(localhost_defaults(app_yaml) - provided_env(mf))
    if leaking:
        found_localhost = True
        note(f"{service}: falls back to a localhost default for {', '.join(leaking)}")
if not found_localhost:
    print("  ok    no service silently falls back to localhost")

print()
if failures:
    print(f"{len(failures)} problem(s) — this deploy would not come up cleanly.")
    sys.exit(1)
print("All k8s environment wiring checks passed.")
