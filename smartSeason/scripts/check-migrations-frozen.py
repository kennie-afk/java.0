#!/usr/bin/env python3
"""Fail the build if a migration that has already shipped was edited.

A Flyway migration is a historical record, not source code. Once it has run against any
database its content is fixed: Flyway checksums the file, and a change — even to a
comment — makes every database that already ran it refuse to start with "Migration
checksum mismatch". That surfaces at deploy time, on the machine that already holds the
data, which is the worst possible place to find out.

This project is at particular risk because its services are generated. tools/generate.py
wipes services/ and rewrites it, and until this was fixed it also stripped comments from
SQL. The sibling SmartRE project was taken down by exactly that: thirteen applied
migrations lost their comments and every service refused to start. generate.py now carries
migrations across the wipe untouched; this check is the belt to that braces.

  --check    [default] fail on any change to a recorded migration
  --update   record new migrations, and intentional changes to ones never deployed
"""

import hashlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FREEZE = ROOT / "scripts" / "migration-freeze.txt"


def migrations():
    for f in sorted(ROOT.glob("services/*/src/main/resources/db/migration/V*__*.sql")):
        yield f.relative_to(ROOT).as_posix(), hashlib.sha256(f.read_bytes()).hexdigest()


def load():
    if not FREEZE.exists():
        return {}
    out = {}
    for line in FREEZE.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            digest, path = line.split("  ", 1)
            out[path] = digest
    return out


def main():
    update = "--update" in sys.argv
    recorded, current = load(), dict(migrations())

    changed = [p for p, d in current.items() if p in recorded and recorded[p] != d]
    removed = [p for p in recorded if p not in current]
    added = [p for p in current if p not in recorded]

    for p in changed:
        print(f"  CHANGED  {p}")
    for p in removed:
        print(f"  REMOVED  {p}")
    for p in added:
        print(f"  new      {p}")

    if update:
        lines = [
            "# SHA-256 of every Flyway migration, recorded when it was written.",
            "# Regenerate with: python3 scripts/check-migrations-frozen.py --update",
            "",
        ] + [f"{d}  {p}" for p, d in sorted(current.items())]
        FREEZE.write_text("\n".join(lines) + "\n")
        print(f"\nRecorded {len(current)} migrations.")
        return 0

    if changed or removed:
        print(f"\n{len(changed) + len(removed)} recorded migration(s) were edited or deleted.")
        print("Restore them, or — if they have genuinely never run anywhere — rerun with --update.")
        return 1

    print(f"{len(current)} migrations, {len(added)} new, none edited.")
    if added:
        print("Run --update to record the new ones.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
