#!/usr/bin/env python3
"""Populates a running SmartSeason demo stack through its own API.

    1. signs in as the demo administrator (registering the organisation the first time),
    2. adds the other people through the team endpoint, one account per role,
    3. runs tools/seed_demo.py, which creates farms, seasons, workers, tasks, market and money data,
    4. links Amina's and Joseph's logins to their worker records, which is what makes
       "My work" and the live board show real names instead of "Unassigned".

Safe to re-run: every record is looked up by a natural key first.

Reads GATEWAY, DEMO_PASSWORD from the environment (scripts/demo.sh exports them from .env.demo).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "tools"))

os.environ.setdefault("GATEWAY", "http://localhost:18080")
os.environ["PASSWORD"] = os.environ.get("DEMO_PASSWORD", "")
if len(os.environ["PASSWORD"]) < 12:
    sys.exit("DEMO_PASSWORD must be set (12+ characters); run this through scripts/demo.sh")

import seed_demo as sd  # noqa: E402

_me = {}


def ME_ID():
    """The signed-in administrator's user id."""
    if "id" not in _me:
        status, body = sd.call("GET", "/api/identity/v1/auth/me")
        _me["id"] = (body or {}).get("id") or (body or {}).get("userId") if status == 200 else None
    return _me["id"]



# email, full name, phone, roles
PEOPLE = [
    ("farmer@smartseason.local", "Wanjiku Kamau", "+254700100001", ["FARMER"]),
    ("manager@smartseason.local", "Brian Otieno", "+254700100002", ["MANAGER"]),
    ("agronomist@smartseason.local", "Dr. Faith Chebet", "+254700100003", ["AGRONOMIST"]),
    ("store@smartseason.local", "Samuel Mutua", "+254700100004", ["STOREKEEPER"]),
    ("finance@smartseason.local", "Esther Njeri", "+254700100005", ["FINANCE"]),
    ("buyer@smartseason.local", "Hassan Abdi", "+254700100006", ["BUYER"]),
    ("amina@smartseason.local", "Amina Wanjiru", "+254700111222", ["WORKER"]),
    ("joseph@smartseason.local", "Joseph Kiptoo", "+254700333444", ["WORKER"]),
]


def ensure_people():
    print("Team")
    status, body = sd.call("GET", "/api/identity/v1/account/team?size=200")
    have = {m["email"]: m["id"] for m in (body or {}).get("content", [])} if status == 200 else {}
    for email, name, phone, roles in PEOPLE:
        if email in have:
            print(f"  exists  {email}")
            continue
        status, body = sd.call("POST", "/api/identity/v1/account/team", {
            "email": email, "fullName": name, "phone": phone, "roles": roles,
            "password": os.environ["PASSWORD"]})
        print(f"  {'created' if status in (200, 201) else 'FAILED '} {email} ({roles[0]})"
              + ("" if status in (200, 201) else f"  HTTP {status}"))
        if status in (200, 201) and body:
            have[email] = body["id"]
    status, body = sd.call("GET", "/api/identity/v1/account/team?size=200")
    return {m["email"]: m["id"] for m in (body or {}).get("content", [])} if status == 200 else have


def link_workers(users):
    print("Linking logins to worker records")
    status, body = sd.call("GET", "/api/workforce/v1/workers?size=200")
    if status != 200:
        print("  skip  (workforce not reachable)")
        return
    by_user = {}
    for worker in body.get("content", []):
        email = {"Amina Wanjiru": "amina@smartseason.local",
                 "Joseph Kiptoo": "joseph@smartseason.local"}.get(worker["fullName"])
        user = users.get(email) if email else None
        if not user:
            continue
        by_user[worker["id"]] = user
        if worker.get("userId") == user:
            print(f"  exists  {worker['fullName']}")
            continue
        status, _ = sd.call("PATCH", f"/api/workforce/v1/workers/{worker['id']}", {"userId": user})
        print(f"  {'linked ' if status == 200 else 'FAILED '} {worker['fullName']}"
              + ("" if status == 200 else f"  HTTP {status}"))
    status, body = sd.call("GET", "/api/task/v1/task-assignments?size=200")
    if status != 200:
        return
    for row in body.get("content", []):
        user = by_user.get(row.get("workerId"))
        if user and row.get("workerUserId") != user:
            status, _ = sd.call("PATCH", f"/api/task/v1/task-assignments/{row['id']}",
                                {"workerUserId": user})
            print(f"  {'linked ' if status == 200 else 'FAILED '} assignment {row['id'][:8]}"
                  + ("" if status == 200 else f"  HTTP {status}"))


def more_data():
    """The records tools/seed_demo.py leaves out, so the screens each role opens first are not empty."""
    ts, day, find, seed = sd.ts, sd.day, sd.find, sd.seed
    farm = find("/api/farm/v1/farms", "name", "Njoro Home Farm")
    plot1 = find("/api/farm/v1/plots", "name", "Plot A1")
    plot2 = find("/api/farm/v1/plots", "name", "Plot A2")
    workers = {w: find("/api/workforce/v1/workers", "fullName", w)
               for w in ("Amina Wanjiru", "Joseph Kiptoo", "Grace Nyambura")}
    season = find("/api/season/v1/seasons", "plotId", plot1)
    order = find("/api/order/v1/orders", "orderNumber", "PO-2026-0001")
    batch = find("/api/inventory/v1/batches", "batchCode", "BATCH-MZ-0001")
    warehouse = find("/api/inventory/v1/warehouses", "name", "Nakuru Central Store")
    vehicle = find("/api/logistics/v1/vehicles", "registrationNo", "KDA 412J")
    driver = find("/api/logistics/v1/drivers", "licenceNumber", "DL-2291884")
    intent = find("/api/payment/v1/payment-intents", "reference", "PI-2026-0001")
    settlement = find("/api/payout/v1/settlements", "settlementNumber", "STL-2026-0001")
    cash = find("/api/ledger/v1/accounts", "accountCode", "1000")
    escrow = find("/api/ledger/v1/accounts", "accountCode", "2000")
    case = find("/api/fraud/v1/fraud-cases", "caseNumber", "FR-2026-0001")
    amina, joseph, grace = workers.values()

    print("More: farm, season, agronomy, weather")
    seed("/api/season/v1/season-stages", "seasonId", {
        "seasonId": season, "stageName": "Tasseling", "sequence": 4, "plannedStart": day(5),
        "plannedEnd": day(-16), "actualStart": day(5), "status": "ACTIVE",
        "notes": "Top-dress due this week."}, "maize stage: tasseling") if season else None
    seed("/api/season/v1/planting-plans", "seasonId", {
        "seasonId": season, "seedRateKgHa": 25.0, "spacingCm": "75x25", "targetPopulation": 53000,
        "fertiliserPlan": "DAP 125 kg/ha at planting; CAN 150 kg/ha at knee height.",
        "irrigationPlan": "Supplementary drip, 20 mm/week in dry spells."}, "maize planting plan") if season else None
    seed("/api/agronomy/v1/scouting-reports", "scoutedAt", {
        "plotId": plot1, "seasonId": season, "scoutedBy": ME_ID(), "scoutedAt": ts(2, 9),
        "pestDiseaseCode": "FAW", "incidencePct": 12.0, "severityScore": 3,
        "latitude": -0.3286, "longitude": 35.9403,
        "notes": "Window-paning on 12% of sampled plants; no whorl damage yet.",
        "status": "OPEN"}, "FAW scouting report on Plot A1") if plot1 else None
    seed("/api/agronomy/v1/crop-playbooks", "stageName", {
        "cropCode": "MAIZE", "stageName": "Tasseling",
        "guidance": "Keep soil moisture up; scout twice a week for fall armyworm.",
        "inputRecommendations": "CAN 150 kg/ha if not yet applied.",
        "riskFactors": "Moisture stress, fall armyworm", "revision": 1}, "maize tasseling playbook")
    for offset, lo, hi, rain in [(0, 11, 24, 1.2), (1, 10, 22, 4.8), (2, 11, 23, 0.0)]:
        seed("/api/weather/v1/forecasts", "forecastFor", {
            "geoCell": "NJORO-0.33S-35.94E", "forecastFor": day(-offset), "issuedAt": ts(0, 5),
            "tempMinC": lo, "tempMaxC": hi, "rainfallMm": rain, "humidityPct": 68, "windKph": 14,
            "conditions": "Light showers" if rain > 1 else "Partly cloudy", "provider": "Sample"},
            f"forecast +{offset}d")
    seed("/api/weather/v1/weather-alerts", "headline", {
        "geoCell": "NJORO-0.33S-35.94E", "alertType": "FROST", "severity": "MEDIUM",
        "startsAt": ts(-2, 1), "endsAt": ts(-2, 7),
        "headline": "Ground frost possible on high plots before dawn",
        "body": "Minimum temperatures near 3 C above 2,100 m. Cover seedlings.",
        "source": "Sample"}, "frost alert")

    print("More: workforce and attendance")
    seed("/api/workforce/v1/worker-contracts", "workerId", {
        "workerId": amina, "farmId": farm, "contractType": "SEASONAL", "startDate": day(120),
        "dailyRate": 450.0, "status": "ACTIVE", "terms": "Seasonal, daily rate."}, "Amina contract") if amina else None
    seed("/api/workforce/v1/gangs", "name", {
        "name": "Top-dressing crew", "farmId": farm, "targetSize": 6, "status": "ACTIVE"}, "Top-dressing crew")
    for who, name, start, end in [(amina, "Amina", 7, 15), (joseph, "Joseph", 6, None)]:
        if not who:
            continue
        seed("/api/attendance/v1/shifts", "startedAt", {
            "workerId": who, "farmId": farm, "startedAt": ts(0, start),
            **({"endedAt": ts(0, end), "durationMinutes": (end - start) * 60 - 45} if end else {}),
            "breakMinutes": 45 if end else 0, "status": "CLOSED" if end else "OPEN"}, f"{name} shift today")
        seed("/api/attendance/v1/clock-events", "occurredAt", {
            "workerId": who, "farmId": farm, "eventType": "CLOCK_IN", "occurredAt": ts(0, start),
            "recordedAt": ts(0, start), "latitude": -0.3286, "longitude": 35.9403, "accuracyM": 8,
            "insideGeofence": True, "mockLocation": False, "offlineSynced": False,
            "verdict": "ACCEPTED"}, f"{name} clock-in")
    if grace:
        seed("/api/attendance/v1/clock-events", "occurredAt", {
            "workerId": grace, "farmId": farm, "eventType": "CLOCK_IN", "occurredAt": ts(1, 8),
            "recordedAt": ts(1, 8), "latitude": -0.3601, "longitude": 35.9702, "accuracyM": 12,
            "insideGeofence": False, "mockLocation": False, "offlineSynced": False,
            "verdict": "FLAGGED", "flagReason": "Clock-in 4.2 km outside the farm perimeter"},
            "Grace flagged clock-in")
    seed("/api/attendance/v1/piece-rate-entries", "recordedAt", {
        "workerId": amina, "farmId": farm, "plotId": plot1, "taskCode": "WEEDING", "quantity": 120,
        "unit": "ROWS", "recordedAt": ts(0, 14), "status": "VERIFIED"}, "Amina rows weeded") if amina else None
    seed("/api/fraud/v1/fraud-signals", "detectedAt", {
        "subjectType": "WORKER", "subjectId": grace, "ruleCode": "PROXY_CLOCK",
        "typology": "PROXY_CLOCK_IN", "score": 72, "detectedAt": ts(1, 8),
        "details": '{"distanceM": 4200, "geofenceRadiusM": 800}', "caseId": case}, "proxy clock-in signal") if grace else None
    seed("/api/fraud/v1/worker-risk-scores", "workerId", {
        "workerId": grace, "farmId": farm, "score": 72, "band": "HIGH", "openCases": 1,
        "lastSignalAt": ts(1, 8)}, "Grace risk score") if grace else None

    print("More: market, stock and transport")
    seed("/api/catalog/v1/products", "name", {
        "commodityCode": "MAIZE", "name": "Njoro Grade 1 maize, 90 kg bag",
        "description": "Dried, graded, bagged at the farm.", "defaultGrade": "GRADE_1",
        "status": "PUBLISHED"}, "maize product")
    seed("/api/pricing/v1/market-indices", "commodityCode", {
        "commodityCode": "MAIZE", "region": "Rift Valley", "periodStart": day(7), "periodEnd": day(0),
        "indexValue": 118.4, "changePct": 2.1, "basis": "Weekly wholesale", "computedAt": ts(0, 2)},
        "maize index")
    seed("/api/pricing/v1/price-quotes", "commodityCode", {
        "commodityCode": "MAIZE", "grade": "GRADE_1", "county": "Nakuru", "quantity": 8000,
        "suggestedPrice": 48.5, "confidence": 0.82, "currency": "KES", "validUntil": ts(-3),
        "rationale": "Nakuru wholesale average plus a Grade 1 premium."}, "maize quote")
    seed("/api/order/v1/order-lines", "orderId", {
        "orderId": order, "commodityCode": "MAIZE", "grade": "GRADE_1", "quantity": 8000,
        "unit": "kg", "unitPrice": 48.5, "lineTotal": 388000.0, "batchId": batch,
        "fulfilledQuantity": 0}, "PO-2026-0001 line") if order else None
    if warehouse:
        item = seed("/api/inventory/v1/stock-items", "batchId", {
            "warehouseId": warehouse, "commodityCode": "MAIZE", "grade": "GRADE_1", "batchId": batch,
            "quantity": 8000, "unit": "kg", "reservedQuantity": 0, "lastCountedAt": ts(1)}, "maize stock")
        if batch:
            seed("/api/inventory/v1/grading-results", "batchId", {
                "batchId": batch, "gradedAt": ts(18), "assignedGrade": "GRADE_1", "defectPct": 0.6,
                "moisturePct": 12.8, "rejectedKg": 120, "notes": "Passed on first sample."}, "maize grading")
    job = seed("/api/logistics/v1/transport-jobs", "jobNumber", {
        "jobNumber": "TJ-2026-0001", "orderId": order, "batchId": batch, "vehicleId": vehicle,
        "driverId": driver, "pickupCounty": "Nakuru", "pickupAt": ts(-1, 7),
        "dropoffCounty": "Nairobi", "distanceKm": 160, "weightKg": 8000, "freightCost": 12000.0,
        "currency": "KES", "requiresColdChain": False, "status": "ASSIGNED"}, "TJ-2026-0001")
    if job:
        seed("/api/logistics/v1/route-stops", "transportJobId", {
            "transportJobId": job, "sequence": 1, "stopType": "PICKUP", "plannedAt": ts(-1, 7), "offRoute": False,
            "notes": "Nakuru Central Store"}, "pickup stop")

    print("More: money")
    seed("/api/payment/v1/mpesa-transactions", "checkoutRequestId", {
        "paymentIntentId": intent, "merchantRequestId": "SIM-MR-0001",
        "checkoutRequestId": "SIM-CO-0001", "mpesaReceiptNumber": "SIM0000001",
        "phoneNumber": "+254700111222", "amount": 388000.0, "transactionType": "STK_PUSH",
        "resultCode": 0, "resultDesc": "Simulated: the request is processed successfully",
        "transactionDate": ts(5), "accountReference": "PI-2026-0001", "status": "SUCCESS"},
        "simulated STK push")
    seed("/api/payment/v1/wallets", "ownerOrgId", {
        "ownerOrgId": sd.ORG, "currency": "KES", "balance": 367400.0, "availableBalance": 367400.0,
        "status": "ACTIVE", "lastTransactionAt": ts(5)}, "cooperative wallet")
    if intent and order:
        seed("/api/payment/v1/escrow-holds", "paymentIntentId", {
            "paymentIntentId": intent, "orderId": order, "amount": 388000.0, "currency": "KES",
            "heldAt": ts(5), "releaseDueAt": ts(-2), "status": "HELD",
            "releaseCondition": "Release on proof of delivery"}, "escrow hold")
    if cash and escrow:
        entry = seed("/api/ledger/v1/journal-entries", "entryNumber", {
            "entryNumber": "JE-2026-0001", "description": "Buyer payment into escrow",
            "sourceEvent": "PaymentSucceeded", "sourceRef": "PI-2026-0001", "postedAt": ts(5),
            "effectiveDate": day(5), "currency": "KES", "totalDebit": 388000.0,
            "totalCredit": 388000.0, "balanced": True,
            "idempotencyKey": "seed-je-2026-0001"}, "JE-2026-0001")
        if entry:
            for acct, code, direction in [(cash, "1000", "DEBIT"), (escrow, "2000", "CREDIT")]:
                seed("/api/ledger/v1/postings", "accountId", {
                    "journalEntryId": entry, "accountId": acct, "accountCode": code,
                    "direction": direction, "amount": 388000.0, "currency": "KES",
                    "postedAt": ts(5), "memo": "PI-2026-0001"}, f"posting {code} {direction}")
    batch_payout = seed("/api/payout/v1/payout-batches", "batchNumber", {
        "batchNumber": "PB-2026-0001", "farmId": farm, "payoutType": "WAGE", "itemCount": 2,
        "totalAmount": 6300.0, "currency": "KES", "scheduledFor": ts(-1, 6), "status": "APPROVED"},
        "PB-2026-0001")
    if batch_payout:
        item1 = seed("/api/payout/v1/payout-items", "payeeId", {
            "batchId": batch_payout, "payeeType": "WORKER", "payeeId": amina, "payeeName": "Amina Wanjiru",
            "payeePhone": "+254700111222", "amount": 3150.0, "currency": "KES", "status": "PENDING",
            "idempotencyKey": "seed-pi-amina"}, "Amina wage") if amina else None
        item2 = seed("/api/payout/v1/payout-items", "payeeId", {
            "batchId": batch_payout, "payeeType": "WORKER", "payeeId": grace, "payeeName": "Grace Nyambura",
            "payeePhone": "+254700555666", "amount": 3150.0, "currency": "KES", "status": "HELD",
            "idempotencyKey": "seed-pi-grace"}, "Grace wage (held)") if grace else None
        if item2:
            seed("/api/payout/v1/payout-holds", "payoutItemId", {
                "payoutItemId": item2, "payeeId": grace, "reason": "FRAUD_CASE", "fraudCaseId": case,
                "amount": 3150.0, "heldAt": ts(1, 9), "status": "ACTIVE",
                "notes": "Held pending FR-2026-0001."}, "Grace payout hold")

    print("More: traceability and evidence")
    for seq, node, ref, where in [(1, "PLOT", "Plot A1", "Njoro"), (2, "HARVEST", "BATCH-MZ-0001", "Njoro"),
                                  (3, "STORAGE", "Nakuru Central Store", "Nakuru")]:
        seed("/api/traceability/v1/trace-links", "sequence", {
            "batchCode": "TRACE-MZ-0001", "sequence": seq, "nodeType": node, "nodeRef": ref,
            "occurredAt": ts(21 - seq * 2), "location": where}, f"trace link {seq} {node}")
    seed("/api/traceability/v1/qr-passes", "passCode", {
        "batchCode": "TRACE-MZ-0001", "passCode": "QR-MZ-0001", "issuedAt": ts(18), "scanCount": 0,
        "publicSummary": '{"crop": "Maize", "grade": "GRADE_1", "farm": "Njoro Home Farm"}', "status": "ACTIVE"}, "QR pass")
    status, body = sd.call("GET", "/api/task/v1/task-assignments?size=50")
    first = (body or {}).get("content", [{}])[0] if status == 200 else {}
    if first.get("id"):
        seed("/api/task/v1/task-evidence", "assignmentId", {
            "assignmentId": first["id"], "workOrderId": first.get("workOrderId"), "evidenceType": "NOTE",
            "capturedAt": ts(0, 10), "mockLocation": False, "notes": "Block finished; photos to follow.",
            "verdict": "ACCEPTED"}, "task evidence")
    if farm:
        seed("/api/farm/v1/farm-memberships", "userId", {
            "farmId": farm, "userId": ME_ID(), "role": "OWNER", "acceptedAt": ts(30), "status": "ACTIVE"},
            "owner membership") if ME_ID() else None


def main():
    if not sd.sign_in():
        print("Could not sign in. Is the stack running?")
        return 1
    print(f"Seeding the demo against {sd.GATEWAY} as {sd.EMAIL}\n")
    users = ensure_people()
    code = sd.main_body()
    more_data()
    link_workers(users)
    return code


if __name__ == "__main__":
    sys.exit(main())
