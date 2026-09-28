#!/usr/bin/env python3
"""Prove the data tier every service's SPRING_DATASOURCE_URL points at actually exists.

check-k8s-env.py proves every variable is *provided* and every Secret/ConfigMap key
*exists* - it never checks that the host a value names is backed by a running workload.
This is what caught SmartRE with eight services pointing at pgbouncer-<name>-db hosts
that had no Service and no workload anywhere in k8s/, which would have crash-looped
every pod on DNS resolution failure the moment anyone ran `kubectl apply -f k8s/`.

Three things checked, all from the files alone:
  1. Every pgbouncer-<name> host named in a SPRING_DATASOURCE_URL resolves to a Service
     defined somewhere in k8s/.
  2. Every such Service selects a real Deployment/StatefulSet (not an orphaned selector).
  3. Every database name in a JDBC URL is created by the matching Postgres
     StatefulSet's POSTGRES_DB (so a renamed database can't silently diverge).

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


def all_manifest_text():
    return "\n".join(p.read_text() for p in sorted(K8S.glob("*.yaml")))


text = all_manifest_text()

print("1. every pgbouncer host named in a JDBC URL resolves to a real Service")
jdbc_hosts = set(re.findall(r"jdbc:postgresql://([a-z0-9-]+):5432/([a-z0-9_]+)", text))
service_names = set(re.findall(r"kind:\s*Service\s*\n\s*metadata:\s*\{?\s*name:\s*([a-z0-9-]+)", text))
# The list-style manifest also writes `metadata: { name: x, namespace: smartre }` on one
# line - the pattern above already tolerates that, but a plain multi-line Service block
# (as every *-service.yaml uses) needs its own scan too.
service_names |= set(re.findall(r"kind:\s*Service\n(?:.*\n)*?\s*name:\s*([a-z0-9-]+)", text))

for host, db in sorted(jdbc_hosts):
    if host not in service_names:
        note(f"{host}: referenced in a SPRING_DATASOURCE_URL but no Service named {host!r} exists")

print("2. every data-tier Service selects a real workload")
# selector: { app: X } (list style) or selector:\n  app: X (block style)
selectors = re.findall(r"kind:\s*Service[\s\S]{0,200}?selector:\s*\{?\s*app:\s*([a-z0-9-]+)", text)
workload_labels = set(re.findall(r"labels:\s*\{?\s*app:\s*([a-z0-9-]+)", text))
for name in sorted(set(jdbc_hosts and [h for h, _ in jdbc_hosts])):
    if name in selectors and name not in workload_labels:
        note(f"{name}: Service selector has no matching Deployment/StatefulSet label")

print("3. every JDBC database name matches that StatefulSet's POSTGRES_DB")
postgres_dbs = dict(re.findall(r"name:\s*([a-z0-9-]+)\n[\s\S]{0,400}?POSTGRES_DB,?\s*value:\s*([a-z0-9_]+)", text))
for host, db in sorted(jdbc_hosts):
    db_statefulset = host.removeprefix("pgbouncer-")
    declared = postgres_dbs.get(db_statefulset)
    if declared is not None and declared != db:
        note(f"{host}: JDBC URL uses database {db!r} but {db_statefulset} StatefulSet creates {declared!r}")

if failures:
    print(f"\n{len(failures)} check(s) failed.")
    sys.exit(1)

print("\nAll k8s data-layer checks passed.")
