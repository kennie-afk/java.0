#!/usr/bin/env bash
# Proves a backup can be restored: dumps a source database, restores it into a throwaway Postgres container and checks the result.
#
#   scripts/backup-drill.sh [source-container]        (default hms-pg, the test database; Compose's is hms-postgres-1)
#
# Checks: every public table has the same row count; the schema history ends at the same migration; row-level security is still enabled on
# the same tables; and the application role, connecting to the restored copy WITHOUT an organisation, sees no patient rows (tenant isolation
# survived the restore). It uses docker only and removes its scratch container. Run it after every change to the schema and on a schedule.
set -euo pipefail
src="${1:-hms-pg}"
user="${HMS_DB_OWNER_USER:-hms}"
srcdb="${HMS_DB_NAME:-hms_test}"
scratch=hms-restore-drill
apppw=drill-app-pw
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
cleanup() { docker rm -f "$scratch" >/dev/null 2>&1 || true; rm -rf "$tmp"; }
trap cleanup EXIT

HMS_BACKUP_VIA="docker:$src" HMS_DB_NAME="$srcdb" "$here/backup.sh" "$tmp" > "$tmp/path"
dump="$(cat "$tmp/path")"

docker rm -f "$scratch" >/dev/null 2>&1 || true
docker run -d --name "$scratch" -e POSTGRES_USER="$user" -e POSTGRES_PASSWORD=drill-owner-pw -e POSTGRES_DB=restored postgres:16-alpine >/dev/null
for _ in $(seq 1 30); do docker exec "$scratch" pg_isready -U "$user" -d restored >/dev/null 2>&1 && break; sleep 1; done
HMS_DB_NAME=restored HMS_DB_APP_PASSWORD="$apppw" "$here/restore.sh" "$dump" "docker:$scratch"

counts() { # $1 container, $2 database -> "table|rows" lines
  docker exec -i "$1" psql -U "$user" -d "$2" -At -c "SELECT format('SELECT %L, count(*) FROM %I;', tablename, tablename) FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename" \
    | docker exec -i "$1" psql -U "$user" -d "$2" -At
}
q() { docker exec -i "$1" psql -U "$user" -d "$2" -At -c "$3"; }

counts "$src" "$srcdb" > "$tmp/a"
counts "$scratch" restored > "$tmp/b"
diff "$tmp/a" "$tmp/b" || { echo "DRILL FAILED: row counts differ" >&2; exit 1; }
tables="$(wc -l < "$tmp/a")"
[ "$tables" -gt 20 ] || { echo "DRILL FAILED: only $tables tables were compared, the source looks empty or unreadable" >&2; exit 1; }
[ "$(q "$src" "$srcdb" "SELECT max(version::int) FROM flyway_schema_history WHERE success")" = "$(q "$scratch" restored "SELECT max(version::int) FROM flyway_schema_history WHERE success")" ] \
  || { echo "DRILL FAILED: schema history differs" >&2; exit 1; }
[ "$(q "$src" "$srcdb" "SELECT string_agg(relname, ',' ORDER BY relname) FROM pg_class WHERE relrowsecurity")" = "$(q "$scratch" restored "SELECT string_agg(relname, ',' ORDER BY relname) FROM pg_class WHERE relrowsecurity")" ] \
  || { echo "DRILL FAILED: row-level security differs" >&2; exit 1; }
owner_sees="$(q "$scratch" restored "SELECT count(*) FROM patients")"
app_sees="$(docker exec -i -e PGPASSWORD="$apppw" "$scratch" psql -h 127.0.0.1 -U hms_app -d restored -At -c "SELECT count(*) FROM patients")"
[ "$app_sees" = "0" ] || { echo "DRILL FAILED: the application role sees $app_sees patient rows with no organisation set" >&2; exit 1; }
echo "DRILL OK: $tables tables with identical row counts; schema history, row-level security and tenant isolation intact (owner sees $owner_sees patients, the application role without an organisation sees 0)."
