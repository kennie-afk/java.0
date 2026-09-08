#!/usr/bin/env python3
"""Checks the permission matrix for holes that would leak data.

The one that matters: granting OWN to a role on a service whose rows are not
actually narrowed. The @PreAuthorize annotation would let the call through and
the generated list endpoint would return every row in the tenant — a leak that
looks like a working feature.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tools"))

import rbac  # noqa: E402
from catalogue import SERVICES  # noqa: E402

problems = []

known = {
    name[:-len("-service")] if name.endswith("-service") else name
    for name in (spec["name"] for spec in SERVICES)
}

for slug, grants in rbac.MATRIX.items():
    if slug not in known:
        problems.append(f"{slug}: no such service")
    for role, level in grants.items():
        if role not in rbac.ROLES:
            problems.append(f"{slug}: '{role}' is not a role")
        if level == rbac.OWN and (slug, role) not in rbac.OWN_SCOPED:
            problems.append(
                f"{slug}/{role}: OWN granted but the rows are not narrowed. "
                f"Add it to OWN_SCOPED once a scoped endpoint exists, or drop the grant.")

# Every service should appear, so a new one cannot default to invisible.
for slug in sorted(known - set(rbac.MATRIX)):
    problems.append(f"{slug}: missing from the matrix; only ADMIN would reach it")

if problems:
    print("permission matrix problems:")
    print("\n".join(f"  {problem}" for problem in problems))
    sys.exit(1)

print(f"matrix is consistent: {len(rbac.MATRIX)} services, {len(rbac.ROLES)} roles, "
      f"{len(rbac.OWN_SCOPED)} own-scoped endpoint(s)")
