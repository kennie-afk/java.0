#!/usr/bin/env bash
# Takes a consistent logical backup of the HMS database (pg_dump custom format) and writes a SHA-256 beside it.
#
#   scripts/backup.sh <out-dir>
#
# Where the database is (choose one):
#   HMS_BACKUP_VIA=docker:<container>    a Postgres container, e.g. the Compose one (default user hms, database hms)
#   HMS_BACKUP_VIA=kubectl               the StatefulSet in namespace hms (HMS_K8S_NAMESPACE, HMS_K8S_POD to override)
#   HMS_BACKUP_VIA=local                 pg_dump on this machine; set PGHOST PGUSER PGPASSWORD PGDATABASE as usual
# HMS_DB_OWNER_USER / HMS_DB_NAME override the user (hms) and database (hms).
#
# This is a DUMP, not point-in-time recovery: you lose whatever happened after it. For a hospital, a managed Postgres with WAL archiving
# (or an operator such as CloudNativePG) gives point-in-time recovery; use this as the second, independent copy and keep it off the cluster.
# The dump holds patient data: encrypt it before it leaves the machine (for example `age -r <key> -o file.age file`) and do not put it in git.
set -euo pipefail
out="${1:?usage: backup.sh <out-dir>}"
user="${HMS_DB_OWNER_USER:-hms}"
db="${HMS_DB_NAME:-hms}"
via="${HMS_BACKUP_VIA:-docker:hms-postgres-1}"
mkdir -p "$out"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
file="$out/hms-$stamp.dump"
case "$via" in
  docker:*) docker exec "${via#docker:}" pg_dump -U "$user" -d "$db" -Fc --no-password > "$file" ;;
  kubectl)  kubectl -n "${HMS_K8S_NAMESPACE:-hms}" exec "${HMS_K8S_POD:-postgres-0}" -- pg_dump -U "$user" -d "$db" -Fc > "$file" ;;
  local)    pg_dump -U "${PGUSER:-$user}" -d "${PGDATABASE:-$db}" -Fc > "$file" ;;
  *) echo "unknown HMS_BACKUP_VIA=$via" >&2; exit 2 ;;
esac
[ -s "$file" ] || { echo "backup is empty: $file" >&2; rm -f "$file"; exit 1; }
sha256sum "$file" | awk '{print $1}' > "$file.sha256"
echo "$file"
