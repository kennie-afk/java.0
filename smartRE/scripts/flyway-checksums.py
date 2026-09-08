#!/usr/bin/env python3
"""Compare each service's migration files against the checksums recorded in its database.

Flyway refuses to start when an already-applied migration file no longer hashes to what
the database recorded — that is the whole point of the check, and it is right to do it.
This script says *which* files diverged, so you can decide whether the file is wrong (the
usual case: someone edited an applied migration, and it should be restored) or the record
is wrong (rarer: the file was already deployed under different content).

  --check   report only, exit non-zero on any divergence   [default]
  --repair  rewrite flyway_schema_history to match the files on disk

--repair is only ever safe when you have confirmed the SQL itself is unchanged and the
difference is cosmetic, because it makes the database accept the file without re-running
it. If the SQL genuinely changed, you want a new migration, not a repair.
"""
import re
import subprocess
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# service directory -> (compose service running its database, database name).
# Resolved to a container id through compose rather than hardcoded, because a recreated
# container can carry a hash prefix in its name.
SERVICES = {
    "user-service": ("user-db", "user_db"),
    "verification-service": ("verification-db", "verification_db"),
    "property-service": ("property-db", "property_db"),
    "viewing-service": ("viewing-db", "viewing_db"),
    "payment-service": ("payment-db", "payment_db"),
    "review-service": ("review-db", "review_db"),
    "notification-service": ("notification-db", "notification_db"),
    "property-management-service": ("pms-db", "pms_db"),
}


def container_id(compose_service):
    out = subprocess.run(
        ["docker", "compose", "ps", "-q", compose_service],
        cwd=ROOT, capture_output=True, text=True,
    )
    return out.stdout.strip().split("\n")[0] or None


def flyway_checksum(path):
    """Flyway's CRC32: over each line's bytes, line terminators excluded."""
    crc = 0
    with open(path, "rb") as fh:
        raw = fh.read()
    text = raw.decode("utf-8-sig")
    for line in text.splitlines():
        crc = zlib.crc32(line.encode("utf-8"), crc)
    # Flyway stores it as a signed 32-bit int.
    return crc - 0x100000000 if crc >= 0x80000000 else crc


def psql(compose_service, db, sql):
    cid = container_id(compose_service)
    if cid is None:
        return None, f"{compose_service} is not running"
    out = subprocess.run(
        ["docker", "exec", cid, "psql", "-U", "postgres", "-d", db, "-tAF|", "-c", sql],
        capture_output=True, text=True,
    )
    if out.returncode != 0:
        return None, out.stderr.strip()
    return out.stdout.strip(), None


def main():
    repair = "--repair" in sys.argv
    divergent = []

    for service, (container, db) in SERVICES.items():
        mig_dir = ROOT / service / "src/main/resources/db/migration"
        if not mig_dir.is_dir():
            continue
        rows, err = psql(container, db, "select version, checksum from flyway_schema_history where version is not null")
        if rows is None:
            print(f"{service}: cannot reach {db} ({err.splitlines()[-1] if err else 'no output'})")
            continue
        recorded = {}
        for line in filter(None, rows.split("\n")):
            version, checksum = line.split("|")
            recorded[version] = int(checksum) if checksum else None

        by_version = {}
        for f in mig_dir.glob("V*__*.sql"):
            m = re.match(r"V(\d+)__", f.name)
            if m:
                by_version[str(int(m.group(1)))] = f

        mismatches = []
        for version, checksum in sorted(recorded.items(), key=lambda kv: int(kv[0])):
            f = by_version.get(version)
            if f is None:
                mismatches.append((version, checksum, None, "applied but no file on disk"))
                continue
            actual = flyway_checksum(f)
            if checksum != actual:
                mismatches.append((version, checksum, actual, f.name))

        if not mismatches:
            print(f"{service:30} {len(recorded)} applied, all match")
            continue

        for version, recorded_sum, actual, name in mismatches:
            print(f"{service:30} V{version}: db={recorded_sum} file={actual}  {name}")
            divergent.append((service, container, db, version, actual, name))

        if repair:
            for _, _, _, version, actual, name in [d for d in divergent if d[0] == service]:
                if actual is None:
                    print(f"  skipped V{version}: no file to take a checksum from")
                    continue
                _, err = psql(container, db,
                              f"update flyway_schema_history set checksum = {actual} where version = '{version}'")
                print(f"  repaired V{version} -> {actual}" if not err else f"  FAILED V{version}: {err}")

    print()
    if divergent and not repair:
        print(f"{len(divergent)} migration(s) diverge from what the database recorded.")
        print("Restore the file from version control if the SQL is unchanged; otherwise write a new migration.")
        return 1
    if not divergent:
        print("Every applied migration matches its file.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
