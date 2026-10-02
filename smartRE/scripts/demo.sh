#!/usr/bin/env bash
#
# One command to a running, populated SmartRE demo.
#
#   ./scripts/demo.sh            start (building anything missing), seed, print where to go
#   ./scripts/demo.sh reset      throw the demo database away and start from empty
#   ./scripts/demo.sh down       stop everything (data is kept)
#   ./scripts/demo.sh seed       only (re)run the seed against a running stack
#   ./scripts/demo.sh status     what is up, and how much memory it takes
#
# Add OBSERVABILITY=1 to also bring up Prometheus, Grafana, Loki and kafka-ui.
set -euo pipefail
cd "$(dirname "$0")/.."

ENV_FILE="${ENV_FILE:-.env.demo}"
[ -f "$ENV_FILE" ] || { echo "missing $ENV_FILE"; exit 1; }
set -a; . "./$ENV_FILE"; set +a

DC="docker compose --env-file $ENV_FILE -f docker-compose.yml -f docker-compose.demo.yml"
[ "${OBSERVABILITY:-0}" = "1" ] && DC="$DC --profile observability"
export COMPOSE_CMD="docker compose --env-file $ENV_FILE -f docker-compose.yml -f docker-compose.demo.yml"
export GATEWAY="http://localhost:${GATEWAY_PORT:-8480}"

SERVICES=(user-service verification-service property-service viewing-service payment-service
          review-service notification-service property-management-service api-gateway)

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }

need_image() { [ -n "$(docker images -q "$1")" ]; }

build_missing() {
  # Built one at a time: Maven for all nine at once runs a small machine out of memory.
  for s in "${SERVICES[@]}"; do
    if ! need_image "smartre-$s:latest"; then
      say "Building $s (first run only)"
      $DC build "$s"
    fi
  done
  if ! need_image "smartre-web-demo:latest"; then
    say "Building the web app (first run only)"
    $DC build web
  fi
}

case "${1:-up}" in
  down)   $DC down; exit 0 ;;
  reset)  $DC down -v; shift || true ;;
  status) $DC ps; docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' | grep smartre-demo | sort; exit 0 ;;
  seed)   python3 scripts/demo_seed.py; exit $? ;;
  up)     ;;
  *)      echo "usage: $0 [up|reset|down|seed|status]"; exit 2 ;;
esac

build_missing

say "Starting the stack (about 3 GiB of memory, 2-4 minutes on a cold start)"
$DC up -d --no-build

say "Waiting for everything to report healthy"
for i in $(seq 1 90); do
  bad=$($DC ps --format '{{.Name}} {{.Health}}' | grep -E "service|gateway|web|kafka|redis" | grep -vc "healthy" || true)
  [ "$bad" = "0" ] && break
  sleep 5
done
[ "$bad" = "0" ] || { echo "not everything became healthy - see: $DC ps"; exit 1; }

say "Seeding demo data through the API"
python3 scripts/demo_seed.py

cat <<MSG

  SmartRE is ready.

    Web app      http://localhost:${WEB_PORT:-3400}
    API gateway  http://localhost:${GATEWAY_PORT:-8480}   (Swagger per service is not exposed; use the web app)

  Sign in with any account below. The password for all of them is:  ${SIGN_IN_AS_PASSWORD}
  (the login page also has a "Sign in as" picker)

    demo.admin@smartre.test          ADMIN     queues, revenue, users, reports
    demo.landlord@smartre.test       LANDLORD  two buildings, 20 units, rent roll
    wanjiru.properties@smartre.test  LANDLORD  a 42-unit portfolio
    demo.seller@smartre.test         SELLER    six verified listings
    demo.buyer@smartre.test          BUYER     viewings, deposits, reviews
    david.kimani@example.co.ke       TENANT    a live tenancy with invoices and receipts

  M-Pesa is simulated: nothing leaves this machine and no money moves.
  Use a phone number ending in 00 to see a customer decline the prompt.

MSG
