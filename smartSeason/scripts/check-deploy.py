#!/usr/bin/env python3
"""Prove the manifests can start every service before applying them.

Four ways a deploy fails quietly, all checkable from the files alone. Each of these was
written after it found something real in a sibling project:

  1. A service reads ${VAR} with no default. Spring refuses to start and the pod
     crash-loops with an error naming the property, not the missing variable.
  2. A manifest references a Secret or ConfigMap key that does not exist. The pod never
     starts at all — CreateContainerConfigError, before any application log.
  3. A ConfigMap key no manifest reads. Harmless to boot, but it makes the ConfigMap look
     authoritative when it is inert, so editing it does nothing.
  4. A service falls back to a localhost default that no manifest overrides. This is the
     quietest of the four: nothing errors, the pod is healthy, and it simply behaves as
     though it were on a laptop.

Exits non-zero on any finding so CI can gate on it.
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


def env_in(path):
    return set(re.findall(r"- name: ([A-Z][A-Z0-9_]*)", path.read_text()))


def required_env(app_yaml):
    out = set()
    for m in re.finditer(r"\$\{([A-Za-z0-9_.]+)(:[^}]*)?\}", app_yaml.read_text()):
        name, default = m.group(1), m.group(2)
        if default is None and name.isupper():
            out.add(name)
    return out


def localhost_defaults(app_yaml):
    out = set()
    for m in re.finditer(r"\$\{([A-Z][A-Z0-9_]*):([^}]*)\}", app_yaml.read_text()):
        if "localhost" in m.group(2) or "127.0.0.1" in m.group(2):
            out.add(m.group(1))
    return out


def defined_keys(path, block):
    """Keys under a top-level `data:` or `stringData:` block.

    Anchored to the start of a line: splitting on the bare word matches `metadata:` too,
    which silently returns the wrong block and reports Secret keys as ConfigMap ones.
    """
    text = path.read_text()
    m = re.search(rf"^{block}:\s*$", text, re.M)
    if not m:
        return set()
    rest = text[m.end():]
    # Stop at the next document or top-level key.
    end = re.search(r"^(---|\S)", rest, re.M)
    body = rest[: end.start()] if end else rest
    return set(re.findall(r"^  ([A-Z][A-Z0-9_]*):", body, re.M))


def referenced(kind):
    keys = set()
    for f in K8S.glob("*.yaml"):
        for m in re.finditer(rf"{kind}:\s*\n\s*name: \S+\s*\n\s*key: (\S+)", f.read_text()):
            keys.add(m.group(1))
    return keys


print("1. mandatory env vars vs. what each Deployment provides")
for app_yaml in sorted(ROOT.glob("services/*/src/main/resources/application.yaml")):
    service = app_yaml.relative_to(ROOT).parts[1]
    mf = K8S / f"{service}.yaml"
    if not mf.exists():
        note(f"{service}: no k8s manifest")
        continue
    missing = sorted(required_env(app_yaml) - env_in(mf))
    if missing:
        note(f"{service}: manifest never sets {', '.join(missing)}")
print(f"  {len(list(ROOT.glob('services/*')))} services checked")

print()
print("2. Secret and ConfigMap keys referenced vs. defined")
shared = K8S / "00-namespace-config.yaml"
for label, block, kind in [
    ("ConfigMap smartseason-config", "data", "configMapKeyRef"),
    ("Secret smartseason-secrets", "stringData", "secretKeyRef"),
]:
    undefined = sorted(referenced(kind) - defined_keys(shared, block))
    if undefined:
        note(f"{label}: referenced but not defined: {', '.join(undefined)}")
    else:
        print(f"  ok    {label}")

print()
print("3. ConfigMap keys that no manifest reads")
inert = sorted(defined_keys(shared, "data") - referenced("configMapKeyRef"))
if inert:
    note("smartseason-config: " + ", ".join(inert) + " are set but wired to nothing")
else:
    print("  ok    every ConfigMap key reaches a container")

print()
print("4. localhost defaults that no manifest overrides")
found = False
for app_yaml in sorted(ROOT.glob("services/*/src/main/resources/application.yaml")):
    service = app_yaml.relative_to(ROOT).parts[1]
    mf = K8S / f"{service}.yaml"
    if not mf.exists():
        continue
    leaking = sorted(localhost_defaults(app_yaml) - env_in(mf))
    if leaking:
        found = True
        note(f"{service}: falls back to a localhost default for {', '.join(leaking)}")
if not found:
    print("  ok    no service silently falls back to localhost")

print()
if failures:
    print(f"{len(failures)} problem(s) — this deploy would not come up cleanly.")
    sys.exit(1)
print("All deployment checks passed.")
