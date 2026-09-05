#!/usr/bin/env bash
set -uo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
pass=0
fail=0

check() {
  local label="$1" actual="$2" expected="$3"
  if [ "$actual" = "$expected" ]; then
    printf '  ok    %-50s %s\n' "$label" "$actual"; pass=$((pass + 1))
  else
    printf '  FAIL  %-50s got %s want %s\n' "$label" "$actual" "$expected"; fail=$((fail + 1))
  fi
}

field() { python3 -c "import json,sys;print(json.load(sys.stdin).get('$1',''))" 2>/dev/null; }

echo "Event path: attendance clock event -> Kafka -> fraud rules engine"
echo

SUFFIX="$RANDOM$RANDOM"
TOKEN=$(curl -sS -X POST "$GATEWAY/api/identity/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"organisationName\":\"Event Path Farm\",\"fullName\":\"Ops\",\"email\":\"ops-$SUFFIX@example.com\",\"password\":\"a-strong-passphrase\",\"orgType\":\"FARM\"}" \
  | field accessToken)
[ -n "$TOKEN" ] && echo "  ok    registered a tenant" && pass=$((pass+1)) || { echo "  FAIL  could not register"; exit 1; }

auth=(-H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json')
WORKER_ID=$(python3 -c "import uuid;print(uuid.uuid4())")
FARM_ID=$(python3 -c "import uuid;print(uuid.uuid4())")
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)

echo
echo "publishing a fraudulent clock event (mocked GPS, weak biometric, outside geofence)"
CODE=$(curl -sS -o /tmp/clockevent.json -w '%{http_code}' -X POST "$GATEWAY/api/attendance/v1/clock-events" "${auth[@]}" \
  -d "{\"workerId\":\"$WORKER_ID\",\"farmId\":\"$FARM_ID\",\"eventType\":\"CLOCK_IN\",\"occurredAt\":\"$NOW\",\"recordedAt\":\"$NOW\",\"latitude\":-1.2921,\"longitude\":36.8219,\"accuracyM\":8.0,\"biometricScore\":0.31,\"insideGeofence\":false,\"mockLocation\":true,\"offlineSynced\":false,\"deviceId\":\"device-1\",\"clientEventId\":\"evt-$SUFFIX\",\"verdict\":\"FLAGGED\"}")
check "attendance accepted the clock event" "$CODE" "201"

echo
echo "waiting for fraud-service to consume and evaluate"
FOUND=""
for i in $(seq 1 30); do
  BODY=$(curl -sS "$GATEWAY/api/fraud/v1/fraud-cases?size=50" "${auth[@]}" 2>/dev/null)
  MATCH=$(printf '%s' "$BODY" | python3 -c "
import json,sys
try: d=json.load(sys.stdin)
except Exception: sys.exit()
for c in d.get('content',[]):
    if c.get('subjectId')=='$WORKER_ID':
        print(json.dumps(c)); break
" 2>/dev/null)
  if [ -n "$MATCH" ]; then FOUND="$MATCH"; echo "  consumed after ~${i}s"; break; fi
  sleep 1
done

if [ -z "$FOUND" ]; then
  echo "  FAIL  no fraud case was opened for the worker within 30s"
  fail=$((fail+1))
else
  check "a fraud case was opened for the worker" "$(printf '%s' "$FOUND" | field subjectId)" "$WORKER_ID"
  check "typology is proxy clock-in" "$(printf '%s' "$FOUND" | field typology)" "PROXY_CLOCK_IN"
  check "severity is critical" "$(printf '%s' "$FOUND" | field severity)" "CRITICAL"
  check "payout was held" "$(printf '%s' "$FOUND" | field payoutHeld)" "True"
  check "case is open" "$(printf '%s' "$FOUND" | field status)" "OPEN"
fi

echo
echo "worker risk score"
RISK=$(curl -sS "$GATEWAY/api/fraud/v1/worker-risk-scores?size=50" "${auth[@]}" 2>/dev/null | python3 -c "
import json,sys
try: d=json.load(sys.stdin)
except Exception: sys.exit()
for r in d.get('content',[]):
    if r.get('workerId')=='$WORKER_ID':
        print(r.get('band','')); break
" 2>/dev/null)
check "risk band was raised to critical" "$RISK" "CRITICAL"

echo
echo "=============================="
echo "passed: $pass   failed: $fail"
echo "=============================="
[ "$fail" -eq 0 ]
