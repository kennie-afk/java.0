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


def dairy_data():
    """A small herd with two weeks of milk, deliveries, one withdrawal and one expected calving."""
    ts, day, find, seed = sd.ts, sd.day, sd.find, sd.seed
    farm = find("/api/farm/v1/farms", "name", "Njoro Home Farm")
    if not farm:
        return
    print("Dairy: herd, yields, deliveries, events")
    herd = [
        ("KE-001", "Wambui", "Friesian", "MILKING", 21.0), ("KE-002", "Chepkoech", "Ayrshire", "MILKING", 16.5),
        ("KE-003", "Njeri", "Friesian", "MILKING", 19.0), ("KE-004", "Atieno", "Jersey", "MILKING", 12.0),
        ("KE-005", "Mueni", "Friesian", "DRY", 0.0), ("KE-006", "Zawadi", "Ayrshire", "HEIFER", 0.0),
    ]
    cows = {}
    for tag, name, breed, status, _ in herd:
        cows[tag] = seed("/api/farm/v1/cows", "tagNo", {
            "farmId": farm, "tagNo": tag, "name": name, "breed": breed, "sex": "FEMALE", "status": status,
            "birthDate": day(365 * 4), "acquiredOn": day(900)}, f"cow {tag}")
    status, body = sd.call("GET", "/api/farm/v1/milk-yields?size=1")
    if status == 200 and (body or {}).get("content"):
        return
    for back in range(13, -1, -1):
        for i, (tag, _, _, st, litres) in enumerate(herd):
            if st != "MILKING" or not cows.get(tag):
                continue
            for session, share in (("MORNING", 0.55), ("EVENING", 0.45)):
                wobble = ((back * 7 + i * 3) % 5 - 2) * 0.3
                seed("/api/farm/v1/milk-yields", "notes", {
                    "cowId": cows[tag], "farmId": farm, "recordedOn": day(back), "session": session,
                    "litres": round(litres * share + wobble, 1), "notes": f"{tag} {day(back)} {session}"},
                    f"yield {tag} {day(back)} {session}")
    for back in range(13, -1, -1):
        total = round(sum(h[4] for h in herd if h[3] == "MILKING") * 0.97, 1)
        rejected = 6.0 if back == 5 else 0.0
        seed("/api/farm/v1/milk-deliveries", "receiptNo", {
            "farmId": farm, "deliveredOn": day(back), "buyerName": "Njoro Dairy Co-operative",
            "receiptNo": f"NDC-{1000 + 13 - back}", "litresDelivered": total, "litresRejected": rejected,
            "fatPct": 3.9, "snfPct": 8.6, "temperatureC": 4.5, "alcoholTestPassed": rejected == 0.0,
            "pricePerLitre": 48.0, "currency": "KES", "status": "PARTIAL" if rejected else "DELIVERED"},
            f"delivery {day(back)}")
    seed("/api/farm/v1/cow-health-events", "description", {
        "cowId": cows["KE-002"], "farmId": farm, "eventDate": day(2), "eventType": "MASTITIS",
        "description": "Mastitis, left rear quarter; treated, milk held back", "medicine": "Intramammary antibiotic",
        "withdrawalEndsOn": day(-3), "vetName": "Dr. Kiprop"}, "mastitis treatment")
    seed("/api/farm/v1/cow-health-events", "description", {
        "cowId": cows["KE-001"], "farmId": farm, "eventDate": day(20), "eventType": "VACCINATION",
        "description": "Foot-and-mouth booster"}, "FMD vaccination")
    seed("/api/farm/v1/breeding-events", "notes", {
        "cowId": cows["KE-003"], "farmId": farm, "eventDate": day(60), "eventType": "PREGNANCY_CHECK",
        "method": "AI", "outcome": "Pregnant", "expectedCalvingOn": day(-20), "notes": "KE-003 confirmed"},
        "KE-003 pregnancy")


def fill_gaps():
    """Records for screens a first-time viewer opens and finds empty: market carts, returns, disputes,
    stock reservations, input use, delivery proof, cold chain, crop vigour, crew membership, fraud
    evidence, product variants, certifications and KYC. Technical tables (OTP challenges, saga states,
    provider callbacks, upload tickets, anchors, balances) stay empty on purpose: the system fills them."""
    ts, day, find, seed = sd.ts, sd.day, sd.find, sd.seed
    farm = find("/api/farm/v1/farms", "name", "Njoro Home Farm")
    plot1 = find("/api/farm/v1/plots", "name", "Plot A1")
    plot2 = find("/api/farm/v1/plots", "name", "Plot A2")
    season = find("/api/season/v1/seasons", "plotId", plot1)
    order = find("/api/order/v1/orders", "orderNumber", "PO-2026-0001")
    batch = find("/api/inventory/v1/batches", "batchCode", "BATCH-MZ-0001")
    item = find("/api/inventory/v1/stock-items", "batchId", batch) if batch else None
    product = find("/api/catalog/v1/products", "name", "Njoro Grade 1 maize, 90 kg bag")
    gang = find("/api/workforce/v1/gangs", "name", "Top-dressing crew")
    job = find("/api/logistics/v1/transport-jobs", "jobNumber", "TJ-2026-0001")
    case = find("/api/fraud/v1/fraud-cases", "caseNumber", "FR-2026-0001")
    org = find("/api/identity/v1/organisations", "name", "Green Acres Cooperative")
    workers = {w: find("/api/workforce/v1/workers", "fullName", w) for w in ("Amina Wanjiru", "Joseph Kiptoo", "Grace Nyambura")}
    amina, joseph, grace = workers.values()
    store = find("/api/identity/v1/users", "email", "store@smartseason.local")
    farmer = find("/api/identity/v1/users", "email", "farmer@smartseason.local")

    print("Fill: catalogue, market and orders")
    seed("/api/catalog/v1/certifications", "code", {"code": "GLOBALGAP", "name": "GLOBALG.A.P.", "issuingBody": "FoodPLUS GmbH",
        "description": "Good agricultural practice for export produce.", "validityMonths": 12}, "GLOBALG.A.P.")
    seed("/api/catalog/v1/certifications", "code", {"code": "KEBS", "name": "KEBS Standardisation Mark", "issuingBody": "Kenya Bureau of Standards",
        "description": "Product quality mark for the local market.", "validityMonths": 12}, "KEBS mark")
    if product:
        for sku, name, size, grade in [("MZ-90-G1", "90 kg bag, Grade 1", 90, "GRADE_1"), ("MZ-50-G1", "50 kg bag, Grade 1", 50, "GRADE_1"), ("MZ-90-G2", "90 kg bag, Grade 2", 90, "GRADE_2")]:
            seed("/api/catalog/v1/product-variants", "sku", {"productId": product, "sku": sku, "variantName": name,
                "packSize": size, "packUnit": "kg", "grade": grade, "active": True}, f"variant {sku}")
    if org and product:
        cart = seed("/api/order/v1/carts", "buyerOrgId", {"buyerOrgId": org, "currency": "KES", "status": "OPEN", "expiresAt": ts(-24)}, "open cart")
        if cart:
            seed("/api/order/v1/cart-items", "cartId", {"cartId": cart, "listingId": product, "commodityCode": "MAIZE",
                "quantity": 2000, "unit": "kg", "unitPrice": 48.5, "sellerOrgId": org}, "cart line: maize 2,000 kg")
    if order:
        seed("/api/order/v1/order-returns", "orderId", {"orderId": order, "quantity": 90, "reason": "One bag arrived wet and was refused at the mill.",
            "requestedAt": ts(1), "refundAmount": 4365.0, "status": "REQUESTED"}, "return request")
        if org:
            seed("/api/order/v1/disputes", "orderId", {"orderId": order, "raisedByOrgId": org, "category": "QUALITY",
                "description": "Moisture reading at the mill was 14.2%, above the agreed 13%.", "raisedAt": ts(1), "status": "UNDER_REVIEW"}, "quality dispute")

    print("Fill: inventory and logistics")
    if item:
        seed("/api/inventory/v1/reservations", "stockItemId", {"stockItemId": item, "orderId": order, "quantity": 2000,
            "reservedAt": ts(1), "expiresAt": ts(-48), "status": "HELD"}, "stock held for the order")
    if farm and plot1:
        issue = seed("/api/inventory/v1/input-issues", "inputCode", {"farmId": farm, "plotId": plot1, "seasonId": season, "inputCode": "CAN-50",
            "inputName": "CAN fertiliser, 50 kg bag", "quantity": 12, "unit": "bag", "issuedTo": farmer, "issuedBy": store,
            "issuedAt": ts(2, 7), "unitCost": 3400.0, "expectedRatePerHa": 3.0, "status": "ISSUED"}, "CAN issued for Plot A1")
        if issue:
            seed("/api/inventory/v1/input-consumptions", "inputIssueId", {"inputIssueId": issue, "farmId": farm, "plotId": plot1, "seasonId": season,
                "inputCode": "CAN-50", "quantity": 10, "unit": "bag", "appliedAt": ts(1, 11), "appliedBy": farmer, "areaCoveredHa": 3.2,
                "varianceKg": -4.0}, "CAN applied on Plot A1")
    if job:
        seed("/api/logistics/v1/proofs-of-delivery", "transportJobId", {"transportJobId": job, "receivedBy": "Wanjiru, Kabete Mills",
            "receivedAt": ts(0, 14), "latitude": -1.2634, "longitude": 36.7378, "deliveredWeightKg": 7940.0, "varianceKg": -60.0,
            "notes": "Gate weighbridge ticket 20457.", "disputed": False}, "proof of delivery")
        for hours, temp, breach in [(6, 4.2, False), (5, 4.6, False), (4, 5.1, False), (3, 8.9, True), (2, 4.8, False)]:
            seed("/api/logistics/v1/cold-chain-readings", "recordedAt", {"transportJobId": job, "recordedAt": ts(0, 14 - hours),
                "temperatureC": temp, "humidityPct": 78, "deviceId": "COLD-0007", "breach": breach}, f"cold chain {temp} C")

    print("Fill: crops, crews, fraud, traceability, identity")
    for plot, back, ndvi in [(plot1, 14, 0.58), (plot1, 7, 0.69), (plot1, 0, 0.74), (plot2, 7, 0.51), (plot2, 0, 0.55)]:
        if plot:
            seed("/api/weather/v1/ndvi-readings", "tileUrl", {"plotId": plot, "geoCell": "NJORO-0.33S-35.94E", "capturedOn": day(back),
                "ndvi": ndvi, "cloudCoverPct": 8.0, "satellite": "Sentinel-2", "tileUrl": f"s2://njoro/{plot}/{day(back)}"}, f"NDVI {ndvi} {day(back)}")
    if gang:
        for who, role, name in [(amina, "LEAD", "Amina"), (joseph, "MEMBER", "Joseph"), (grace, "MEMBER", "Grace")]:
            if who:
                seed("/api/workforce/v1/gang-memberships", "workerId", {"gangId": gang, "workerId": who, "joinedAt": ts(10), "role": role}, f"{name} in crew")
    if case:
        seed("/api/fraud/v1/fraud-evidence", "label", {"caseId": case, "label": "Clock-ins from one device", "evidenceType": "DEVICE_REUSE",
            "payload": '{"deviceId": "AND-77F1", "workers": 3, "days": 4}', "weight": 0.35, "collectedAt": ts(1)}, "device reuse evidence")
    seed("/api/traceability/v1/cert-evidence", "certificateNo", {"batchCode": "BATCH-MZ-0001", "farmId": farm, "certificationCode": "KEBS",
        "certificateNo": "KEBS-2026-04417", "issuedBy": "Kenya Bureau of Standards", "issuedOn": day(60), "expiresOn": day(-300), "verified": True,
        "verifiedAt": ts(30)}, "KEBS certificate") if farm else None
    if farmer:
        seed("/api/identity/v1/kyc-records", "subjectId", {"subjectId": farmer, "subjectType": "USER", "idNumber": "29****418", "status": "VERIFIED",
            "reviewedBy": ME_ID(), "reviewNotes": "ID matched to the registry extract."}, "farmer KYC")


def main():
    if not sd.sign_in():
        print("Could not sign in. Is the stack running?")
        return 1
    print(f"Seeding the demo against {sd.GATEWAY} as {sd.EMAIL}\n")
    users = ensure_people()
    code = sd.main_body()
    more_data()
    dairy_data()
    fill_gaps()
    link_workers(users)
    return code


if __name__ == "__main__":
    sys.exit(main())
