#!/usr/bin/env python3
"""Compare each service's migrations against the checksums its database recorded.

Flyway refuses to start when an applied migration no longer hashes to what the database
holds, and it is right to: the file is supposed to be a fixed historical record. This
reports which files diverged so you can decide whether the file is wrong (usually — it was
edited or regenerated) or the record is (rarely).

  --check   report only, exit non-zero on divergence  [default]
  --repair  rewrite flyway_schema_history to match the files on disk

--repair is only safe once you have confirmed the SQL itself is unchanged and the
difference is cosmetic, because it makes the database accept a file it never ran. If the
SQL genuinely changed, you want a new migration instead.
"""

import re
import subprocess
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def flyway_checksum(path):
    """Flyway's CRC32: over each line's bytes, line terminators excluded."""
    crc = 0
    text = path.read_bytes().decode("utf-8-sig")
    for line in text.splitlines():
        crc = zlib.crc32(line.encode("utf-8"), crc)
    return crc - 0x100000000 if crc >= 0x80000000 else crc


def psql(db, sql):
    cid = subprocess.run(["docker", "compose", "ps", "-q", "postgres"],
                         cwd=ROOT, capture_output=True, text=True).stdout.strip()
    if not cid:
        return None, "postgres is not running"
    out = subprocess.run(["docker", "exec", cid, "psql", "-U", "postgres", "-d", db,
                          "-tAF|", "-c", sql], capture_output=True, text=True)
    return (out.stdout.strip(), None) if out.returncode == 0 else (None, out.stderr.strip())


def main():
    repair = "--repair" in sys.argv
    divergent = []

    for mig_dir in sorted(ROOT.glob("services/*/src/main/resources/db/migration")):
        service = mig_dir.relative_to(ROOT).parts[1]
        db = service.replace("-service", "") + "_db"
        # A couple of services use a shortened database name.
        rows, err = psql(db, "select version, checksum from flyway_schema_history where version is not null")
        if rows is None:
            for alt in (service.replace("-service", "")[:6] + "_db",):
                rows, err = psql(alt, "select version, checksum from flyway_schema_history where version is not null")
                if rows is not None:
                    db = alt
                    break
        if rows is None:
            print(f"{service:28} database {db} unreachable")
            continue

        recorded = {}
        for line in filter(None, rows.split("\n")):
            version, checksum = line.split("|")
            recorded[version] = int(checksum) if checksum else None

        files = {}
        for f in mig_dir.glob("V*__*.sql"):
            m = re.match(r"V(\d+)__", f.name)
            if m:
                files[str(int(m.group(1)))] = f

        mismatched = []
        for version, checksum in sorted(recorded.items(), key=lambda kv: int(kv[0])):
            f = files.get(version)
            if f is None:
                mismatched.append((version, checksum, None))
                continue
            actual = flyway_checksum(f)
            if checksum != actual:
                mismatched.append((version, checksum, actual))

        if not mismatched:
            print(f"{service:28} {len(recorded)} applied, all match")
            continue

        for version, was, now in mismatched:
            print(f"{service:28} V{version}: db={was} file={now}")
            divergent.append((service, db, version, now))

        if repair:
            for _, d, version, now in [x for x in divergent if x[0] == service]:
                if now is None:
                    print(f"  skipped V{version}: no file to checksum")
                    continue
                _, e = psql(d, f"update flyway_schema_history set checksum = {now} where version = '{version}'")
                print(f"  repaired V{version} -> {now}" if not e else f"  FAILED V{version}: {e}")

    print()
    if divergent and not repair:
        print(f"{len(divergent)} migration(s) diverge from what the database recorded.")
        return 1
    if not divergent:
        print("Every applied migration matches its file.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
