#!/usr/bin/env bash
# One load run against a compose stack, then the integrity check.
#   scripts/loadtest/run.sh [tenants] [terminals-per-tenant] [sales-per-second] [seconds]
# The stack must be up with the load-test override and a bootstrap credential in .env:
#   docker compose -f docker-compose.yml -f docker-compose.loadtest.yml --env-file .env up -d
set -euo pipefail
cd "$(dirname "$0")/../.."
TENANTS=${1:-20}; PER=${2:-10}; RATE=${3:-100}; SECS=${4:-60}
OUT=${OUT:-/tmp/mara-load-$(date +%s).json}
BOOT=$(grep '^MARA_BOOTSTRAP_CREDENTIAL=' .env | cut -d= -f2-)
IDP=$(grep '^MARA_IDENTITY_PORT=' .env | cut -d= -f2- || true); IDP=${IDP:-8081}
SYP=$(grep '^MARA_SYNC_PORT=' .env | cut -d= -f2- || true); SYP=${SYP:-8082}
[ -n "$BOOT" ] || { echo "MARA_BOOTSTRAP_CREDENTIAL is not set in .env"; exit 1; }
CRED=$(curl -s -X POST "http://localhost:$IDP/v1/admin/credentials" -H "Authorization: Bearer $BOOT" -H 'Content-Type: application/json' \
  -d '{"label":"load-test","expiresInHours":4,"scopes":["admin:read","admin:write","platform:tenants"]}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["credential"])')
(cd apps/terminal && LOAD_OPERATOR_CREDENTIAL="$CRED" npx vite-node scripts/loadtest.ts -- \
  --identity "http://localhost:$IDP" --sync "http://localhost:$SYP" \
  --tenants "$TENANTS" --terminals "$PER" --rate "$RATE" --duration "$SECS" --out "$OUT")
python3 scripts/loadtest/verify.py "$OUT"
echo "results: $OUT"
