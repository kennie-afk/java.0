#!/usr/bin/env bash
#
# Seeds a demonstrable slice of SmartRE through the public API.
#
# Everything here goes through the gateway exactly as a browser would — register,
# log in, create a listing, then build the property-management side on top of it.
# Nothing is inserted into a database directly, so if this script runs the API
# genuinely works end to end; if it fails, it fails where a real user would.
#
# Idempotent enough to re-run: registration of an existing email is tolerated and
# the script falls through to login.

set -uo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
PASSWORD="${SEED_PASSWORD:-Passw0rd!seed}"

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$*"; }
die()  { printf '  \033[31m✗\033[0m %s\n' "$*"; exit 1; }

json() { python3 -c "import sys,json;d=json.load(sys.stdin);print(d$1)" 2>/dev/null; }

api() {                     # api METHOD PATH [BODY] [TOKEN]
  local method="$1" path="$2" body="${3:-}" token="${4:-}"
  local args=(-s -X "$method" -H 'Content-Type: application/json' --max-time 30)
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ]  && args+=(-d "$body")
  curl "${args[@]}" "$GATEWAY$path"
}

register_or_login() {       # register_or_login NAME EMAIL ROLE -> token
  local name="$1" email="$2" role="$3"
  api POST /api/auth/register \
      "{\"fullName\":\"$name\",\"email\":\"$email\",\"password\":\"$PASSWORD\",\"phone\":\"+254700000001\",\"role\":\"$role\"}" \
      >/dev/null
  api POST /api/auth/login "{\"email\":\"$email\",\"password\":\"$PASSWORD\"}" | json "['token']"
}

# ---------------------------------------------------------------- wait for API ---

say "Waiting for the gateway"
for i in $(seq 1 60); do
  status=$(curl -s --max-time 5 "$GATEWAY/actuator/health" | json "['status']" || true)
  [ "$status" = "UP" ] && { ok "gateway UP after ${i}0s"; break; }
  [ "$i" = "60" ] && die "gateway never became healthy"
  sleep 10
done

# --------------------------------------------------------------------- accounts ---

say "Accounts"
LANDLORD_TOKEN=$(register_or_login "Grace Njeri"   "landlord@smartre.demo" LANDLORD)
[ -n "$LANDLORD_TOKEN" ] || die "could not obtain a landlord token"
ok "landlord  landlord@smartre.demo"

BUYER_TOKEN=$(register_or_login "Peter Otieno" "buyer@smartre.demo" BUYER)
ok "buyer     buyer@smartre.demo"

# --------------------------------------------------------------------- listings ---

say "Property listing"
PROPERTY_ID=$(api POST /api/properties '{
  "title":"Riverside Court, Westlands",
  "description":"A managed block of eight apartments a short walk from Westlands Road. Secure parking, borehole water, lift access.",
  "propertyType":"APARTMENT","listingType":"RENT",
  "county":"Nairobi","subCounty":"Westlands","city":"Nairobi",
  "locationDescription":"Off Rhapta Road, opposite the shopping centre",
  "latitude":-1.2664,"longitude":36.8029,
  "price":85000,"bedrooms":2,"bathrooms":2,"areaSqm":96.0,"yearBuilt":2019,
  "imageUrls":["https://picsum.photos/seed/riverside1/1200/800","https://picsum.photos/seed/riverside2/1200/800"]
}' "$LANDLORD_TOKEN" | json "['id']")
[ -n "$PROPERTY_ID" ] || die "property was not created"
ok "Riverside Court  $PROPERTY_ID"

# ------------------------------------------------------------------------ units ---

say "Units"
declare -A UNIT_IDS
seed_unit() {               # seed_unit LABEL BEDS BATHS RENT DEPOSIT
  local id
  id=$(api POST /api/units "{\"propertyId\":\"$PROPERTY_ID\",\"label\":\"$1\",\"unitType\":\"APARTMENT\",
        \"bedrooms\":$2,\"bathrooms\":$3,\"sizeSqm\":96.0,\"rentAmount\":$4,\"depositAmount\":$5}" \
        "$LANDLORD_TOKEN" | json "['id']")
  [ -n "$id" ] && { UNIT_IDS["$1"]="$id"; ok "unit $1  KSh $4/month"; } || warn "unit $1 not created"
}
seed_unit "A1" 2 2 85000 170000
seed_unit "A2" 2 2 85000 170000
seed_unit "B1" 3 2 120000 240000
seed_unit "B2" 1 1 55000 110000

# ---------------------------------------------------------------------- tenants ---

say "Tenants"
declare -A TENANT_IDS
seed_tenant() {             # seed_tenant NAME PHONE ID_NUMBER
  local id
  id=$(api POST /api/tenants "{\"fullName\":\"$1\",\"phone\":\"$2\",\"nationalId\":\"$3\",
        \"email\":\"$(echo "$1" | tr 'A-Z ' 'a-z.')@example.co.ke\",
        \"emergencyName\":\"Next of Kin\",\"emergencyPhone\":\"+254711000000\"}" \
        "$LANDLORD_TOKEN" | json "['id']")
  [ -n "$id" ] && { TENANT_IDS["$1"]="$id"; ok "tenant $1"; } || warn "tenant $1 not created"
}
seed_tenant "Mary Wanjiku" "+254722111222" "28394011"
seed_tenant "David Kimani" "+254733444555" "31882094"
seed_tenant "Aisha Hassan" "+254745666777" "29471553"

# ----------------------------------------------------------------------- leases ---

say "Leases"
seed_lease() {              # seed_lease UNIT TENANT RENT START BILLING_DAY
  local id
  id=$(api POST /api/leases "{\"unitId\":\"${UNIT_IDS[$1]}\",\"tenantId\":\"${TENANT_IDS[$2]}\",
        \"startDate\":\"$4\",\"rentAmount\":$3,\"depositAmount\":$(($3*2)),
        \"managementFeePct\":8.0,\"billingDay\":$5,\"paymentFrequency\":\"MONTHLY\",
        \"noticePeriodDays\":60}" "$LANDLORD_TOKEN" | json "['id']")
  [ -n "$id" ] && ok "lease  $1 → $2  (bills on day $5)" || warn "lease $1 → $2 not created"
}
# A lease is created as DRAFT and has to be activated before it counts as a tenancy —
# the domain models signing as a separate act from drafting, and occupancy, rent roll
# and invoicing all key off the activation rather than the record existing.
seed_lease() {              # seed_lease UNIT TENANT RENT START BILLING_DAY
  local id
  id=$(api POST /api/leases "{\"unitId\":\"${UNIT_IDS[$1]}\",\"tenantId\":\"${TENANT_IDS[$2]}\",
        \"startDate\":\"$4\",\"rentAmount\":$3,\"depositAmount\":$(($3*2)),
        \"managementFeePct\":8.0,\"billingDay\":$5,\"paymentFrequency\":\"MONTHLY\",
        \"noticePeriodDays\":60}" "$LANDLORD_TOKEN" | json "['id']")
  if [ -z "$id" ]; then warn "lease $1 -> $2 not created"; return; fi
  local status
  status=$(api PUT "/api/leases/$id/activate" "" "$LANDLORD_TOKEN" | json "['status']")
  if [ "$status" = "ACTIVE" ]; then
    ok "lease  $1 -> $2  ACTIVE  (bills on day $5)"
  else
    warn "lease $1 -> $2 created but did not activate (status: ${status:-unknown})"
  fi
}

seed_lease "A1" "Mary Wanjiku" 85000  "2026-01-01" 1
seed_lease "B1" "David Kimani" 120000 "2026-03-15" 5
seed_lease "B2" "Aisha Hassan" 55000  "2026-06-01" 1
# A2 is left deliberately vacant, so occupancy is not a meaningless 100%.

# ------------------------------------------------------------------ maintenance ---

say "Maintenance"
seed_maintenance() {        # seed_maintenance UNIT CATEGORY PRIORITY TITLE DESCRIPTION
  local id
  id=$(api POST /api/maintenance "{\"unitId\":\"${UNIT_IDS[$1]}\",\"category\":\"$2\",\"priority\":\"$3\",
        \"title\":\"$4\",\"description\":\"$5\"}" "$LANDLORD_TOKEN" | json "['id']")
  [ -n "$id" ] && ok "$3  $4" || warn "maintenance '$4' not created"
}
seed_maintenance "A1" "PLUMBING"  "HIGH"   "Kitchen tap leaking"        "Steady drip from the mixer tap; the cabinet beneath is staining."
seed_maintenance "B1" "ELECTRICAL" "URGENT" "Sockets dead in second bedroom" "No power to any socket in the second bedroom since Tuesday. Lighting works."
seed_maintenance "B2" "GENERAL"   "LOW"    "Repaint balcony railing"    "Rust showing along the balcony railing."

say "Done"
echo "  Landlord:  landlord@smartre.demo / $PASSWORD"
echo "  Buyer:     buyer@smartre.demo / $PASSWORD"
echo "  Property:  $PROPERTY_ID"
