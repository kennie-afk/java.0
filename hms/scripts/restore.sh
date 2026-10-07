#!/usr/bin/env bash
# Restores a dump made by backup.sh into an EMPTY database. It refuses a database that already has tables.
#
#   HMS_DB_APP_PASSWORD=... scripts/restore.sh <dump-file> docker:<container>
#
# Target forms: docker:<container> | kubectl | local (PGHOST PGUSER PGPASSWORD as usual). Variables: HMS_DB_OWNER_USER (hms), HMS_DB_NAME (hms),
# HMS_DB_APP_USER (hms_app), HMS_DB_APP_PASSWORD (required: the application role must exist before the grants in the dump are replayed).
# The owner role must already exist and be the one that owned the original objects (it is the container's POSTGRES_USER in the manifests).
set -euo pipefail
dump="${1:?usage: restore.sh <dump-file> <docker:container|kubectl|local>}"
via="${2:?target missing}"
user="${HMS_DB_OWNER_USER:-hms}"
db="${HMS_DB_NAME:-hms}"
app="${HMS_DB_APP_USER:-hms_app}"
: "${HMS_DB_APP_PASSWORD:?set HMS_DB_APP_PASSWORD (the application role is created before the restore)}"
[ -f "$dump" ] || { echo "no such file: $dump" >&2; exit 2; }
if [ -f "$dump.sha256" ]; then
  [ "$(sha256sum "$dump" | awk '{print $1}')" = "$(cat "$dump.sha256")" ] || { echo "checksum mismatch: the dump is damaged" >&2; exit 1; }
fi
case "$via" in
  docker:*) c="${via#docker:}"; psqlc=(docker exec -i "$c" psql -U "$user" -d "$db" -v ON_ERROR_STOP=1 -At); restorec=(docker exec -i "$c" pg_restore -U "$user" -d "$db" --exit-on-error) ;;
  kubectl)  p="${HMS_K8S_POD:-postgres-0}"; n="${HMS_K8S_NAMESPACE:-hms}"; psqlc=(kubectl -n "$n" exec -i "$p" -- psql -U "$user" -d "$db" -v ON_ERROR_STOP=1 -At); restorec=(kubectl -n "$n" exec -i "$p" -- pg_restore -U "$user" -d "$db" --exit-on-error) ;;
  local)    psqlc=(psql -U "${PGUSER:-$user}" -d "${PGDATABASE:-$db}" -v ON_ERROR_STOP=1 -At); restorec=(pg_restore -U "${PGUSER:-$user}" -d "${PGDATABASE:-$db}" --exit-on-error) ;;
  *) echo "unknown target $via" >&2; exit 2 ;;
esac
existing="$("${psqlc[@]}" -c "SELECT count(*) FROM pg_tables WHERE schemaname = 'public'")"
[ "$existing" = "0" ] || { echo "refusing: the target database already has $existing tables" >&2; exit 1; }
"${psqlc[@]}" -c "DO \$\$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$app') THEN CREATE ROLE $app LOGIN PASSWORD '$HMS_DB_APP_PASSWORD' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS; END IF; END \$\$"
"${restorec[@]}" < "$dump"
echo "restored $dump"
