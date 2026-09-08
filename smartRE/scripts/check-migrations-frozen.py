#!/usr/bin/env python3
"""Fail the build if a migration that has already shipped was edited.

A Flyway migration is a historical record, not source code: once it has run against a
database anywhere, its content is fixed forever. Editing it — even to reword a comment —
changes its checksum, and every database that already ran it will refuse to start with
"Migration checksum mismatch". That failure surfaces at deploy time, on the machine that
already has the data, which is the worst possible place to find out.

This check catches it at commit time instead. `scripts/migration-freeze.txt` records the
SHA-256 of every migration file. Adding a new migration is fine and expected — run
--update to record it. Changing one that is already listed is an error.

  --check    [default] fail on any change to a listed migration
  --update   record hashes for new migrations, and for intentional changes to unshipped ones
"""
import hashlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FREEZE = ROOT / "scripts" / "migration-freeze.txt"


def migrations():
    for f in sorted(ROOT.glob("*/src/main/resources/db/migration/V*__*.sql")):
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
    recorded = load()
    current = dict(migrations())

    changed = [p for p, d in current.items() if p in recorded and recorded[p] != d]
    added = [p for p in current if p not in recorded]
    removed = [p for p in recorded if p not in current]

    for p in changed:
        print(f"  CHANGED  {p}")
    for p in removed:
        print(f"  REMOVED  {p}")
    for p in added:
        print(f"  new      {p}")

    if update:
        lines = ["# SHA-256 of every Flyway migration, recorded the moment it was written.",
                 "# Regenerate with: python3 scripts/check-migrations-frozen.py --update",
                 ""]
        lines += [f"{d}  {p}" for p, d in sorted(current.items())]
        FREEZE.write_text("\n".join(lines) + "\n")
        print(f"\nRecorded {len(current)} migrations.")
        return 0

    if changed or removed:
        print(f"\n{len(changed) + len(removed)} already-recorded migration(s) were edited or deleted.")
        print("Restore them, or — if they have genuinely never run anywhere — rerun with --update.")
        return 1

    print(f"{len(current)} migrations, {len(added)} new, none edited.")
    if added:
        print("Run --update to record the new ones.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
