#!/usr/bin/env bash
set -uo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
pass=0
fail=0

check() {
  local label="$1" actual="$2" expected="$3"
  if [ "$actual" = "$expected" ]; then
    printf '  ok    %-52s %s\n' "$label" "$actual"
    pass=$((pass + 1))
  else
    printf '  FAIL  %-52s got %s want %s\n' "$label" "$actual" "$expected"
    fail=$((fail + 1))
  fi
}

json() { printf '%s' "$1" | python3 -c "import json,sys;print(json.load(sys.stdin).get('$2',''))" 2>/dev/null; }
status() { printf '%s' "$1" | tail -n1; }
body() { printf '%s' "$1" | sed '$d'; }

call() {
  curl -sS -w '\n%{http_code}' "$@" 2>/dev/null
}

echo "SmartSeason smoke test against $GATEWAY"
echo

echo "health"
check "gateway is up" "$(status "$(call "$GATEWAY/actuator/health")")" "200"

echo
echo "registration and login"
SUFFIX="$RANDOM$RANDOM"
EMAIL_A="alpha-$SUFFIX@example.com"
EMAIL_B="beta-$SUFFIX@example.com"

RESP=$(call -X POST "$GATEWAY/api/identity/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"organisationName\":\"Alpha Farms\",\"fullName\":\"Alpha Admin\",\"email\":\"$EMAIL_A\",\"password\":\"a-strong-passphrase\",\"orgType\":\"FARM\"}")
check "register tenant A" "$(status "$RESP")" "201"
TOKEN_A=$(json "$(body "$RESP")" accessToken)
ORG_A=$(json "$(body "$RESP")" organisationId)

RESP=$(call -X POST "$GATEWAY/api/identity/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"organisationName\":\"Beta Coop\",\"fullName\":\"Beta Admin\",\"email\":\"$EMAIL_B\",\"password\":\"a-strong-passphrase\",\"orgType\":\"COOPERATIVE\"}")
check "register tenant B" "$(status "$RESP")" "201"
TOKEN_B=$(json "$(body "$RESP")" accessToken)
ORG_B=$(json "$(body "$RESP")" organisationId)

check "tenants got distinct ids" "$([ "$ORG_A" != "$ORG_B" ] && echo yes || echo no)" "yes"

RESP=$(call -X POST "$GATEWAY/api/identity/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"organisationName\":\"Dup\",\"fullName\":\"Dup\",\"email\":\"$EMAIL_A\",\"password\":\"a-strong-passphrase\"}")
check "duplicate email rejected" "$(status "$RESP")" "409"

RESP=$(call -X POST "$GATEWAY/api/identity/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL_A\",\"password\":\"a-strong-passphrase\"}")
check "login succeeds" "$(status "$RESP")" "200"

RESP=$(call -X POST "$GATEWAY/api/identity/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL_A\",\"password\":\"wrong-password\"}")
check "wrong password refused" "$(status "$RESP")" "422"

RESP=$(call "$GATEWAY/api/identity/v1/auth/me" -H "Authorization: Bearer $TOKEN_A")
check "profile returns the caller" "$(json "$(body "$RESP")" email)" "$EMAIL_A"

echo
echo "authorisation"
check "no token is rejected at the edge" "$(status "$(call "$GATEWAY/api/farm/v1/farms")")" "401"
check "garbage token is rejected" \
  "$(status "$(call "$GATEWAY/api/farm/v1/farms" -H 'Authorization: Bearer not-a-token')")" "401"

echo
echo "farm service"
RESP=$(call -X POST "$GATEWAY/api/farm/v1/farms" \
  -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d '{"name":"Alpha Home Farm","county":"Nakuru","totalAreaHa":12.5,"status":"ACTIVE"}')
check "tenant A creates a farm" "$(status "$RESP")" "201"
FARM_ID=$(json "$(body "$RESP")" id)

RESP=$(call "$GATEWAY/api/farm/v1/farms" -H "Authorization: Bearer $TOKEN_A")
check "tenant A sees its farm" \
  "$(printf '%s' "$(body "$RESP")" | python3 -c "import json,sys;print(json.load(sys.stdin)['totalElements'])" 2>/dev/null)" "1"

RESP=$(call "$GATEWAY/api/farm/v1/farms" -H "Authorization: Bearer $TOKEN_B")
check "tenant B sees none of tenant A's farms" \
  "$(printf '%s' "$(body "$RESP")" | python3 -c "import json,sys;print(json.load(sys.stdin)['totalElements'])" 2>/dev/null)" "0"

RESP=$(call "$GATEWAY/api/farm/v1/farms/$FARM_ID" -H "Authorization: Bearer $TOKEN_B")
check "tenant B cannot fetch tenant A's farm by id" "$(status "$RESP")" "404"

RESP=$(call -X POST "$GATEWAY/api/farm/v1/farms" \
  -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d '{"county":"Nakuru"}')
check "invalid payload rejected" "$(status "$RESP")" "400"
check "validation error is problem+json" "$(json "$(body "$RESP")" code)" "validation-failed"

RESP=$(call "$GATEWAY/api/farm/v1/farms/00000000-0000-0000-0000-000000000000" \
  -H "Authorization: Bearer $TOKEN_A")
check "unknown id returns not-found" "$(json "$(body "$RESP")" code)" "not-found"

echo
echo "=============================="
echo "passed: $pass   failed: $fail"
echo "=============================="
[ "$fail" -eq 0 ]
