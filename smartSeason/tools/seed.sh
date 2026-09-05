#!/usr/bin/env bash
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

create() {
  local path="$1" body="$2" label="$3"
  local id
  id=$(curl -sS -X POST "$GATEWAY$path" "${auth[@]}" -d "$body" | field id)
  if [ -n "$id" ]; then
    say "  created $label"
    printf '%s' "$id"
  else
    say "  skipped $label (service unavailable)"
  fi
}

say "Farms"
FARM1=$(create /api/farm/v1/farms \
  '{"name":"Njoro Home Farm","county":"Nakuru","subCounty":"Njoro","totalAreaHa":24.5,"latitude":-0.3286,"longitude":35.9403,"status":"ACTIVE"}' \
  "Njoro Home Farm")
FARM2=$(create /api/farm/v1/farms \
  '{"name":"Mau Narok Block B","county":"Nakuru","subCounty":"Mau Narok","totalAreaHa":61.0,"latitude":-0.6833,"longitude":35.9000,"status":"ACTIVE"}' \
  "Mau Narok Block B")
create /api/farm/v1/farms \
  '{"name":"Subukia Ridge","county":"Nyandarua","totalAreaHa":13.2,"status":"ACTIVE"}' \
  "Subukia Ridge" > /dev/null

if [ -n "${FARM1:-}" ]; then
  say "Plots"
  create "/api/farm/v1/plots" \
    "{\"farmId\":\"$FARM1\",\"name\":\"Plot A1\",\"areaHa\":6.0,\"irrigated\":true,\"currentCrop\":\"MAIZE\",\"status\":\"ACTIVE\"}" \
    "Plot A1" > /dev/null
  create "/api/farm/v1/plots" \
    "{\"farmId\":\"$FARM1\",\"name\":\"Plot A2\",\"areaHa\":4.5,\"irrigated\":false,\"currentCrop\":\"POTATO\",\"status\":\"ACTIVE\"}" \
    "Plot A2" > /dev/null
fi

say "Workers"
create /api/workforce/v1/workers \
  "{\"fullName\":\"Amina Wanjiru\",\"phone\":\"+254700111222\",\"farmId\":\"${FARM1:-}\",\"status\":\"ACTIVE\",\"riskScore\":0}" \
  "Amina Wanjiru" > /dev/null
create /api/workforce/v1/workers \
  "{\"fullName\":\"Joseph Kiptoo\",\"phone\":\"+254700333444\",\"farmId\":\"${FARM1:-}\",\"status\":\"ACTIVE\",\"riskScore\":0}" \
  "Joseph Kiptoo" > /dev/null

say "Marketplace listings"
create /api/marketplace/v1/supply-listings \
  '{"sellerOrgId":"00000000-0000-0000-0000-000000000001","commodityCode":"MAIZE","grade":"GRADE_1","quantity":8000,"unit":"kg","askPrice":48.5,"currency":"KES","county":"Nakuru","status":"ACTIVE"}' \
  "8000 kg maize" > /dev/null
create /api/marketplace/v1/supply-listings \
  '{"sellerOrgId":"00000000-0000-0000-0000-000000000001","commodityCode":"POTATO","grade":"GRADE_2","quantity":3200,"unit":"kg","askPrice":31.0,"currency":"KES","county":"Nyandarua","status":"ACTIVE"}' \
  "3200 kg potato" > /dev/null

say ""
say "Done. Sign in at the web application with:"
say "  email    $EMAIL"
say "  password $PASSWORD"
