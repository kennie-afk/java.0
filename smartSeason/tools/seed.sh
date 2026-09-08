#!/usr/bin/env bash
# Seeds a small, realistic demo dataset. Safe to re-run: every entity is looked
# up by a natural key first, so a second run reports "exists" instead of
# creating a duplicate.
set -uo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
ORG="${ORG:-Green Acres Cooperative}"
EMAIL="${EMAIL:-demo@smartseason.local}"
PASSWORD="${PASSWORD:-a-strong-demo-passphrase}"

say() { printf '%s\n' "$*" >&2; }
field() { python3 -c "import json,sys;print(json.load(sys.stdin).get('$1',''))" 2>/dev/null; }

say "Seeding SmartSeason demo data against $GATEWAY"

TOKEN=$(curl -sS -X POST "$GATEWAY/api/identity/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"organisationName\":\"$ORG\",\"fullName\":\"Demo Administrator\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"orgType\":\"COOPERATIVE\"}" \
  | field accessToken)

if [ -z "$TOKEN" ]; then
  say "Registration failed (the account may already exist); trying to sign in instead."
  TOKEN=$(curl -sS -X POST "$GATEWAY/api/identity/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | field accessToken)
fi

if [ -z "$TOKEN" ]; then
  say "Could not obtain a token. Is the stack running?"
  exit 1
fi
say "Signed in as $EMAIL"

auth=(-H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json')

# Returns the id of the first item in <path> whose <key> equals <value>, or an
# empty string when the collection has no match or the service is unreachable.
lookup() {
  local path="$1" key="$2" value="$3"
  curl -sS "$GATEWAY$path?size=200" "${auth[@]}" 2>/dev/null \
    | KEY="$key" VALUE="$value" python3 -c '
import json, os, sys
key, value = os.environ["KEY"], os.environ["VALUE"]
try:
    rows = json.load(sys.stdin).get("content", [])
except Exception:
    sys.exit(0)
for row in rows:
    if str(row.get(key, "")) == value:
        print(row.get("id", ""))
        break
' 2>/dev/null
}

# find_or_create <collection path> <natural key field> <natural key value> <json body> <label>
find_or_create() {
  local path="$1" key="$2" value="$3" body="$4" label="$5"
  local id
  id=$(lookup "$path" "$key" "$value")
  if [ -n "$id" ]; then
    say "  exists  $label"
    printf '%s' "$id"
    return
  fi
  id=$(curl -sS -X POST "$GATEWAY$path" "${auth[@]}" -d "$body" | field id)
  if [ -n "$id" ]; then
    say "  created $label"
    printf '%s' "$id"
  else
    say "  skipped $label (service unavailable)"
  fi
}

say "Farms"
FARM1=$(find_or_create /api/farm/v1/farms name "Njoro Home Farm" \
  '{"name":"Njoro Home Farm","county":"Nakuru","subCounty":"Njoro","totalAreaHa":24.5,"latitude":-0.3286,"longitude":35.9403,"status":"ACTIVE"}' \
  "Njoro Home Farm")
find_or_create /api/farm/v1/farms name "Mau Narok Block B" \
  '{"name":"Mau Narok Block B","county":"Nakuru","subCounty":"Mau Narok","totalAreaHa":61.0,"latitude":-0.6833,"longitude":35.9000,"status":"ACTIVE"}' \
  "Mau Narok Block B" > /dev/null
find_or_create /api/farm/v1/farms name "Subukia Ridge" \
  '{"name":"Subukia Ridge","county":"Nyandarua","totalAreaHa":13.2,"status":"ACTIVE"}' \
  "Subukia Ridge" > /dev/null

PLOT1=""
PLOT2=""
if [ -n "${FARM1:-}" ]; then
  say "Plots"
  PLOT1=$(find_or_create /api/farm/v1/plots name "Plot A1" \
    "{\"farmId\":\"$FARM1\",\"name\":\"Plot A1\",\"areaHa\":6.0,\"irrigated\":true,\"currentCrop\":\"MAIZE\",\"status\":\"ACTIVE\"}" \
    "Plot A1")
  PLOT2=$(find_or_create /api/farm/v1/plots name "Plot A2" \
    "{\"farmId\":\"$FARM1\",\"name\":\"Plot A2\",\"areaHa\":4.5,\"irrigated\":false,\"currentCrop\":\"POTATO\",\"status\":\"ACTIVE\"}" \
    "Plot A2")
fi

if [ -n "$PLOT1" ]; then
  say "Seasons"
  find_or_create /api/season/v1/seasons plotId "$PLOT1" \
    "{\"plotId\":\"$PLOT1\",\"farmId\":\"$FARM1\",\"cropCode\":\"MAIZE\",\"variety\":\"H614D\",\"startDate\":\"2026-03-15\",\"expectedHarvestDate\":\"2026-08-20\",\"expectedYieldKg\":16200,\"currentStage\":\"TASSELING\",\"status\":\"ACTIVE\"}" \
    "maize on Plot A1" > /dev/null
  find_or_create /api/season/v1/seasons plotId "$PLOT2" \
    "{\"plotId\":\"$PLOT2\",\"farmId\":\"$FARM1\",\"cropCode\":\"POTATO\",\"variety\":\"Shangi\",\"startDate\":\"2026-04-02\",\"expectedHarvestDate\":\"2026-07-28\",\"expectedYieldKg\":9000,\"currentStage\":\"TUBER_BULKING\",\"status\":\"ACTIVE\"}" \
    "potato on Plot A2" > /dev/null
fi

say "Workers"
find_or_create /api/workforce/v1/workers fullName "Amina Wanjiru" \
  "{\"fullName\":\"Amina Wanjiru\",\"phone\":\"+254700111222\",\"farmId\":\"${FARM1:-}\",\"status\":\"ACTIVE\",\"riskScore\":0}" \
  "Amina Wanjiru" > /dev/null
find_or_create /api/workforce/v1/workers fullName "Joseph Kiptoo" \
  "{\"fullName\":\"Joseph Kiptoo\",\"phone\":\"+254700333444\",\"farmId\":\"${FARM1:-}\",\"status\":\"ACTIVE\",\"riskScore\":0}" \
  "Joseph Kiptoo" > /dev/null

say "Marketplace listings"
find_or_create /api/marketplace/v1/supply-listings commodityCode "MAIZE" \
  '{"sellerOrgId":"00000000-0000-0000-0000-000000000001","commodityCode":"MAIZE","grade":"GRADE_1","quantity":8000,"unit":"kg","askPrice":48.5,"currency":"KES","county":"Nakuru","status":"ACTIVE"}' \
  "8000 kg maize" > /dev/null
find_or_create /api/marketplace/v1/supply-listings commodityCode "POTATO" \
  '{"sellerOrgId":"00000000-0000-0000-0000-000000000001","commodityCode":"POTATO","grade":"GRADE_2","quantity":3200,"unit":"kg","askPrice":31.0,"currency":"KES","county":"Nyandarua","status":"ACTIVE"}' \
  "3200 kg potato" > /dev/null

say ""
say "Done. Sign in at the web application with:"
say "  email    $EMAIL"
say "  password $PASSWORD"
