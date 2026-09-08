#!/usr/bin/env python3
"""Checks every Deployment can scale and survive a node drain.

Two gaps this catches, both of which look fine until the cluster is under load
or being upgraded:

  - A Deployment with no HorizontalPodAutoscaler never grows. Fixed replicas
    means a traffic spike is absorbed by latency, then by errors.
  - A Deployment with no PodDisruptionBudget can lose every replica at once to a
    node drain, so a routine cluster upgrade takes the service offline.

It also refuses a PDB that cannot ever be satisfied: minAvailable at or above the
autoscaler's floor blocks voluntary eviction for ever, which stops node drains
rather than protecting them.
"""
import glob
import os
import sys

try:
    import yaml
except ImportError:
    print("pyyaml is required: pip install pyyaml")
    sys.exit(2)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "k8s")

deployments, hpas, pdbs = {}, {}, {}

for path in sorted(glob.glob(os.path.join(ROOT, "*.yaml"))):
    for doc in yaml.safe_load_all(open(path)):
        if not doc:
            continue
        kind = doc.get("kind")
        name = doc.get("metadata", {}).get("name")
        if kind == "Deployment":
            deployments[name] = doc
        elif kind == "HorizontalPodAutoscaler":
            hpas[name] = doc
        elif kind == "PodDisruptionBudget":
            pdbs[name] = doc

problems = []

for name in sorted(deployments):
    if name not in hpas:
        problems.append(f"{name}: no HorizontalPodAutoscaler; replicas are fixed")
    if name not in pdbs:
        problems.append(f"{name}: no PodDisruptionBudget; a drain can take every replica")

for name, hpa in sorted(hpas.items()):
    if name not in deployments:
        problems.append(f"{name}: HPA targets a Deployment that does not exist")
    floor = hpa.get("spec", {}).get("minReplicas", 1)
    pdb = pdbs.get(name)
    if pdb:
        want = pdb.get("spec", {}).get("minAvailable")
        if isinstance(want, int) and want >= floor:
            problems.append(
                f"{name}: PDB minAvailable {want} >= HPA minReplicas {floor}, "
                f"so no pod may ever be evicted and node drains will hang")

if problems:
    print("scaling and availability problems:")
    print("\n".join(f"  {p}" for p in problems))
    sys.exit(1)

print(f"all {len(deployments)} deployments have an autoscaler and a disruption budget")
