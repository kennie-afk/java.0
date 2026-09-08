#!/usr/bin/env python3
"""Seeds a coherent demo dataset across every SmartSeason service.

Safe to re-run: each record is looked up by a natural key first, so a second run
reports "exists" rather than creating duplicates. Services that are not running
are skipped with a note instead of failing the run.

    GATEWAY=http://localhost:18080 python3 tools/seed_demo.py
"""
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta, timezone

GATEWAY = os.environ.get("GATEWAY", "http://localhost:8080")
EMAIL = os.environ.get("EMAIL", "demo@smartseason.local")
PASSWORD = os.environ.get("PASSWORD", "a-strong-demo-passphrase")

TOKEN = None
created = skipped = existing = 0

CHECK_ONLY = "--check" in sys.argv
SPECS = {}
problems = []


def load_specs():
    """Maps API path -> {field: spec} from the same catalogue the services use."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from catalogue import SERVICES as CATALOGUE
    from gen_support import parse_entity
    for spec in CATALOGUE:
        name = spec["name"]
        slug = name[:-len("-service")] if name.endswith("-service") else name
        for _entity, table, fields in (parse_entity(e) for e in spec["entities"]):
            path = f"/api/{slug}/v1/{table.replace('_', '-')}"
            SPECS[path] = {f.name: f for f in fields}


def check(path, record, label):
    """Reports unknown and missing-required fields for one seed payload."""
    fields = SPECS.get(path)
    if fields is None:
        problems.append(f"{label}: no such collection {path}")
        return
    for key in record:
        if key not in fields:
            problems.append(f"{label}: unknown field '{key}' on {path}")
    for name, field in fields.items():
        if field.notnull and record.get(name) is None:
            problems.append(f"{label}: missing required '{name}' on {path}")
        value = record.get(name)
        if value is not None and field.enum_values and value not in field.enum_values:
            problems.append(
                f"{label}: '{name}' is '{value}', not one of "
                f"{'|'.join(field.enum_values)} on {path}")


def call(method, path, body=None, timeout=20):
    url = f"{GATEWAY}{path}"
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Accept", "application/json")
    if data is not None:
        request.add_header("Content-Type", "application/json")
    if TOKEN:
        request.add_header("Authorization", f"Bearer {TOKEN}")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read()
            return response.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            return error.code, json.loads(raw)
        except Exception:
            return error.code, None
    except Exception:
        return 0, None


def sign_in():
    global TOKEN
    status, body = call("POST", "/api/identity/v1/auth/login",
                        {"email": EMAIL, "password": PASSWORD})
    if status == 200 and body:
        TOKEN = body["accessToken"]
        return True
    status, body = call("POST", "/api/identity/v1/auth/register", {
        "organisationName": "Green Acres Cooperative", "fullName": "Demo Administrator",
        "email": EMAIL, "password": PASSWORD, "orgType": "COOPERATIVE"})
    if status in (200, 201) and body:
        TOKEN = body["accessToken"]
        return True
    return False


def find(path, key, value):
    """Id of the first record in <path> whose <key> equals <value>."""
    status, body = call("GET", f"{path}?size=200")
    if status != 200 or not body:
        return None
    for row in body.get("content", []):
        if str(row.get(key, "")) == str(value):
            return row.get("id")
    return None


def seed(path, key, record, label=None):
    """Creates one record unless a row with the same natural key already exists."""
    global created, skipped, existing
    label = label or record.get(key, path)
    if CHECK_ONLY:
        check(path, record, label)
        return "check"
    status, _ = call("GET", f"{path}?size=1")
    if status != 200:
        print(f"  skip    {label}  (service not reachable)")
        skipped += 1
        return None

    found = find(path, key, record[key])
    if found:
        print(f"  exists  {label}")
        existing += 1
        return found

    status, body = call("POST", path, record)
    if status in (200, 201) and body:
        print(f"  created {label}")
        created += 1
        return body.get("id")

    detail = (body or {}).get("detail") or (body or {}).get("title") or f"HTTP {status}"
    print(f"  FAILED  {label}  ({detail})")
    skipped += 1
    return None


def ts(days_ago=0, hour=8):
    moment = datetime.now(timezone.utc) - timedelta(days=days_ago)
    return moment.replace(hour=hour, minute=0, second=0, microsecond=0).isoformat().replace("+00:00", "Z")


def day(days_ago=0):
    return (date.today() - timedelta(days=days_ago)).isoformat()


ORG = "00000000-0000-0000-0000-000000000001"


def main():
    if CHECK_ONLY:
        load_specs()
        main_body()
        if problems:
            print("\n".join(problems))
            print(f"\n{len(problems)} problem(s)")
            return 1
        print("every seed payload matches the catalogue")
        return 0
    if not sign_in():
        print("Could not sign in. Is the stack running?")
        return 1
    print(f"Seeding demo data against {GATEWAY} as {EMAIL}\n")
    return main_body()


def main_body():

    print("Farm")
    farm = seed("/api/farm/v1/farms", "name", {
        "name": "Njoro Home Farm", "county": "Nakuru", "subCounty": "Njoro",
        "totalAreaHa": 24.5, "latitude": -0.3286, "longitude": 35.9403, "status": "ACTIVE"})
    seed("/api/farm/v1/farms", "name", {
        "name": "Mau Narok Block B", "county": "Nakuru", "subCounty": "Mau Narok",
        "totalAreaHa": 61.0, "latitude": -0.6833, "longitude": 35.9, "status": "ACTIVE"})
    seed("/api/farm/v1/farms", "name", {
        "name": "Subukia Ridge", "county": "Nyandarua", "subCounty": "Ol Kalou",
        "totalAreaHa": 13.2, "status": "ACTIVE"})

    plot1 = plot2 = None
    if farm:
        plot1 = seed("/api/farm/v1/plots", "name", {
            "farmId": farm, "name": "Plot A1", "areaHa": 6.0, "irrigated": True,
            "currentCrop": "MAIZE", "status": "ACTIVE"})
        plot2 = seed("/api/farm/v1/plots", "name", {
            "farmId": farm, "name": "Plot A2", "areaHa": 4.5, "irrigated": False,
            "currentCrop": "POTATO", "status": "ACTIVE"})
        seed("/api/farm/v1/soil-profiles", "labName", {
            "plotId": plot1, "sampledAt": day(45), "ph": 5.8, "nitrogenPpm": 22.0,
            "phosphorusPpm": 14.5, "potassiumPpm": 180.0, "organicCarbonPct": 2.1,
            "texture": "Clay loam", "labName": "Crop Nutrition Labs Nakuru"},
            "soil profile for Plot A1")

    print("Season")
    if plot1:
        seed("/api/season/v1/seasons", "plotId", {
            "plotId": plot1, "farmId": farm, "cropCode": "MAIZE", "variety": "H614D",
            "startDate": day(176), "expectedHarvestDate": day(18), "expectedYieldKg": 16200,
            "currentStage": "TASSELING", "status": "ACTIVE"}, "maize on Plot A1")
    if plot2:
        seed("/api/season/v1/seasons", "plotId", {
            "plotId": plot2, "farmId": farm, "cropCode": "POTATO", "variety": "Shangi",
            "startDate": day(159), "expectedHarvestDate": day(42), "expectedYieldKg": 9000,
            "currentStage": "TUBER_BULKING", "status": "ACTIVE"}, "potato on Plot A2")
    seed("/api/season/v1/stage-templates", "stageName", {
        "cropCode": "MAIZE", "stageName": "Tasseling", "sequence": 4, "durationDays": 21,
        "description": "Tassels emerge; the crop is most sensitive to moisture stress.",
        "keyActivities": "Top-dress nitrogen, scout for fall armyworm"}, "maize tasseling stage")

    print("Agronomy")
    seed("/api/agronomy/v1/advisories", "title", {
        "seasonId": None, "plotId": plot1, "cropCode": "MAIZE",
        "title": "Fall armyworm pressure rising in Njoro",
        "body": "Scouting found 12% leaf damage on Plot A1. Spray at first sign of "
                "window-paning, early morning or late evening.",
        "severity": "HIGH", "source": "AGRONOMIST", "issuedAt": ts(2)},
        "fall armyworm advisory")
    seed("/api/agronomy/v1/pest-diseases", "code", {
        "code": "FAW", "commonName": "Fall armyworm", "scientificName": "Spodoptera frugiperda",
        "type": "PEST", "affectedCrops": "MAIZE,SORGHUM",
        "symptoms": "Window-paning on young leaves, ragged holes, frass in the whorl.",
        "management": "Early scouting, timely spraying, push-pull intercropping."},
        "fall armyworm")

    print("Weather")
    seed("/api/weather/v1/weather-stations", "externalId", {
        "externalId": "KMD-NJORO-01", "name": "Njoro Agromet Station", "county": "Nakuru",
        "latitude": -0.3286, "longitude": 35.9403, "elevationM": 2166,
        "provider": "Kenya Meteorological Department",
        "active": True}, "Njoro agromet station")

    print("Device registry")
    device = seed("/api/device-registry/v1/devices", "serialNumber", {
        "serialNumber": "SP-2026-0001", "deviceType": "SOIL_PROBE", "farmId": farm,
        "plotId": plot1, "status": "ACTIVE", "firmwareVersion": "1.4.2"},
        "soil probe SP-2026-0001")
    seed("/api/device-registry/v1/firmware-releases", "releaseVersion", {
        "releaseVersion": "1.4.2", "deviceType": "SOIL_PROBE",
        "artifactUrl": "https://firmware.smartseason.local/soil-probe/1.4.2.bin",
        "checksum": "sha256:7d3f1c9a2b8e4d6f5a0c1b2e3d4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f",
        "releaseNotes": "Battery reporting fix.", "mandatory": False,
        "publishedAt": ts(30)}, "firmware 1.4.2")

    print("Telemetry")
    if device:
        seed("/api/telemetry-ingest/v1/telemetry-readings", "metric", {
            "deviceId": device, "metric": "soil_moisture_pct", "value": 27.4,
            "unit": "%", "recordedAt": ts(0, 6), "receivedAt": ts(0, 6), "quality": "GOOD"},
            "soil moisture reading")

    print("Automation")
    seed("/api/automation/v1/automation-rules", "name", {
        "name": "Irrigate Plot A1 below 25% moisture", "plotId": plot1,
        "triggerMetric": "soil_moisture_pct", "operator": "LT", "threshold": 25.0,
        "actionType": "IRRIGATE", "cooldownSeconds": 7200, "enabled": True},
        "irrigation rule")

    print("Workforce")
    worker = seed("/api/workforce/v1/workers", "fullName", {
        "fullName": "Amina Wanjiru", "phone": "+254700111222", "farmId": farm,
        "status": "ACTIVE", "riskScore": 0}, "Amina Wanjiru")
    seed("/api/workforce/v1/workers", "fullName", {
        "fullName": "Joseph Kiptoo", "phone": "+254700333444", "farmId": farm,
        "status": "ACTIVE", "riskScore": 12}, "Joseph Kiptoo")
    seed("/api/workforce/v1/workers", "fullName", {
        "fullName": "Grace Nyambura", "phone": "+254700555666", "farmId": farm,
        "status": "ACTIVE", "riskScore": 0}, "Grace Nyambura")
    seed("/api/workforce/v1/wage-rates", "taskCode", {
        "taskCode": "GENERAL_LABOUR", "farmId": farm, "rateType": "DAILY",
        "amount": 450.0, "currency": "KES", "effectiveFrom": day(90)},
        "general labour day rate")

    print("Attendance")
    seed("/api/attendance/v1/geofences", "name", {
        "farmId": farm, "name": "Njoro Home Farm perimeter", "centerLat": -0.3286,
        "centerLng": 35.9403, "radiusM": 800, "active": True}, "farm perimeter geofence")

    print("Task")
    orders = [
        ("WO-2026-0001", "Top-dress nitrogen on Plot A1", "HIGH", 4.0, plot1,
         "Apply CAN at 150 kg/ha along the planting rows. Do not broadcast: the "
         "crop is at tasseling and leaf scorch costs yield.",
         ["Collect 12 bags of CAN from the store",
          "Confirm the spreader is calibrated to 150 kg/ha",
          "Apply along rows 1-40",
          "Photograph the finished block",
          "Return unused bags and sign the store register"]),
        ("WO-2026-0002", "Scout Plot A2 for late blight", "URGENT", 2.0, plot2,
         "Walk the plot in a W pattern and record incidence at 20 points. Rain "
         "last week puts the crop in the blight window.",
         ["Walk the W transect",
          "Record incidence at 20 points",
          "Photograph any lesions",
          "Report to the agronomist the same day"]),
        ("WO-2026-0003", "Repair the eastern perimeter fence", "NORMAL", 6.0, None,
         "Two spans are down near the gate. Restring and tension both.",
         ["Cut and remove the damaged wire",
          "Restring both spans",
          "Tension to spec",
          "Clear offcuts from the field"]),
    ]
    order_ids = {}
    for code, title, priority, hours, plot, description, steps in orders:
        oid = seed("/api/task/v1/work-orders", "taskCode", {
            "farmId": farm, "plotId": plot, "taskCode": code, "title": title,
            "description": description, "priority": priority,
            "estimatedHours": hours, "status": "OPEN", "dueDate": day(-2)}, code)
        order_ids[code] = oid
        if oid:
            for index, label in enumerate(steps, start=1):
                seed("/api/task/v1/checklist-items", "label", {
                    "workOrderId": oid, "label": label, "sequence": index,
                    "required": index <= 3, "completed": False},
                    f"  objective {index} of {code}")

    # Three assignments in three different states, so the live board has
    # something to show the moment it is opened: one finished, one running,
    # one waiting for the worker to press Start.
    admin = None
    status, me = call("GET", "/api/identity/v1/auth/me")
    if status == 200 and me:
        admin = me.get("id") or me.get("userId")
    admin = admin or ORG

    workers = {}
    status, body = call("GET", "/api/workforce/v1/workers?size=50")
    if status == 200 and body:
        workers = {row["fullName"]: row["id"] for row in body.get("content", [])}

    plan = [
        ("WO-2026-0001", "Amina Wanjiru", "COMPLETED", ts(0, 7), ts(0, 7), ts(0, 11)),
        ("WO-2026-0002", "Joseph Kiptoo", "IN_PROGRESS", ts(0, 6), ts(0, 6), None),
        ("WO-2026-0003", "Grace Nyambura", "ASSIGNED", None, None, None),
    ]
    for code, worker_name, state, accepted, started, completed in plan:
        oid = order_ids.get(code)
        wid = workers.get(worker_name)
        if not oid or not wid:
            continue
        record = {
            "workOrderId": oid, "workerId": wid, "assignedBy": admin,
            "assignedAt": ts(1, 17), "status": state,
        }
        if accepted:
            record["acceptedAt"] = accepted
        if started:
            record["startedAt"] = started
        if completed:
            record["completedAt"] = completed
        seed("/api/task/v1/task-assignments", "workOrderId", record,
             f"{code} to {worker_name} ({state})")

    print("Fraud")
    seed("/api/fraud/v1/fraud-rules", "code", {
        "code": "PROXY_CLOCK", "typology": "PROXY_CLOCK_IN", "name": "Clock-in outside geofence",
        "expression": "clock_event.distance_m > geofence.radius_m",
        "severity": "HIGH", "weight": 40, "enabled": True, "autoHoldPayout": True},
        "proxy clock-in rule")
    if worker:
        seed("/api/fraud/v1/fraud-cases", "caseNumber", {
            "caseNumber": "FR-2026-0001", "subjectType": "WORKER", "subjectId": worker,
            "farmId": farm, "typology": "GHOST_ATTENDANCE", "severity": "HIGH",
            "confidence": 0.87, "openedAt": ts(3), "status": "INVESTIGATING",
            "payoutHeld": True}, "FR-2026-0001")

    print("Catalog")
    for code, name, unit, perishable, category, shelf in [
            ("MAIZE", "Maize", "kg", False, "CEREAL", 365),
            ("POTATO", "Potato", "kg", True, "TUBER", 90),
            ("BEANS", "Beans", "kg", False, "LEGUME", 300),
            ("AVOCADO", "Avocado", "kg", True, "FRUIT", 21)]:
        seed("/api/catalog/v1/commodities", "code", {
            "code": code, "name": name, "category": category, "defaultUnit": unit,
            "perishable": perishable, "shelfLifeDays": shelf}, f"commodity {name}")
    seed("/api/catalog/v1/grade-standards", "grade", {
        "commodityCode": "MAIZE", "grade": "GRADE_1",
        "criteria": "Moisture below 13.5%, no visible aflatoxin, foreign matter under 1%.",
        "moisturePctMax": 13.5, "maxDefectPct": 1.0, "revision": 1},
        "grade 1 maize standard")

    print("Marketplace")
    seed("/api/marketplace/v1/supply-listings", "commodityCode", {
        "sellerOrgId": ORG, "commodityCode": "MAIZE", "grade": "GRADE_1", "quantity": 8000,
        "unit": "kg", "askPrice": 48.5, "currency": "KES", "county": "Nakuru",
        "status": "ACTIVE"}, "8000 kg maize")
    seed("/api/marketplace/v1/demand-posts", "commodityCode", {
        "buyerOrgId": ORG, "commodityCode": "POTATO", "quantity": 5000, "unit": "kg",
        "bidPrice": 33.0, "currency": "KES", "deliveryCounty": "Nairobi City",
        "neededBy": day(-14), "recurring": False, "status": "OPEN"},
        "5000 kg potato wanted")

    print("Pricing")
    seed("/api/pricing/v1/price-series", "commodityCode", {
        "commodityCode": "MAIZE", "county": "Nakuru", "marketName": "Nakuru Wholesale",
        "grade": "GRADE_1", "observedOn": day(1), "unit": "kg", "avgPrice": 47.25,
        "minPrice": 44.0, "maxPrice": 51.0, "currency": "KES", "source": "NAFIS",
        "volumeKg": 42000}, "maize price series")

    print("Order")
    seed("/api/order/v1/orders", "orderNumber", {
        "orderNumber": "PO-2026-0001", "buyerOrgId": ORG, "sellerOrgId": ORG,
        "subtotal": 388000.0, "deliveryFee": 12000.0, "platformFee": 19400.0,
        "totalAmount": 419400.0, "currency": "KES",
        "status": "CONFIRMED", "placedAt": ts(5)}, "PO-2026-0001")

    print("Inventory")
    warehouse = seed("/api/inventory/v1/warehouses", "name", {
        "name": "Nakuru Central Store", "county": "Nakuru", "capacityKg": 250000,
        "coldChain": False, "status": "ACTIVE"}, "Nakuru Central Store")
    if warehouse:
        seed("/api/inventory/v1/batches", "batchCode", {
            "batchCode": "BATCH-MZ-0001", "commodityCode": "MAIZE", "warehouseId": warehouse,
            "farmId": farm, "plotId": plot1, "grossWeightKg": 8120, "netWeightKg": 8000,
            "harvestedOn": day(20), "receivedAt": ts(18), "moisturePct": 12.8,
            "grade": "GRADE_1", "status": "GRADED"}, "BATCH-MZ-0001")

    print("Logistics")
    seed("/api/logistics/v1/vehicles", "registrationNo", {
        "registrationNo": "KDA 412J", "vehicleType": "TRUCK", "capacityKg": 10000,
        "coldChain": False, "odometerKm": 184300, "status": "AVAILABLE"}, "KDA 412J")
    seed("/api/logistics/v1/drivers", "licenceNumber", {
        "fullName": "Peter Mwangi", "phone": "+254701222333", "licenceNumber": "DL-2291884",
        "status": "AVAILABLE"}, "driver Peter Mwangi")

    print("Payment")
    seed("/api/payment/v1/payment-intents", "reference", {
        "reference": "PI-2026-0001", "amount": 388000.0, "currency": "KES",
        "method": "MPESA_STK", "purpose": "ORDER", "status": "SUCCEEDED",
        "idempotencyKey": "seed-pi-2026-0001", "initiatedAt": ts(5), "escrow": True,
        "payerPhone": "+254700111222"}, "PI-2026-0001")

    print("Ledger")
    for code, name, atype, normal in [
            ("1000", "Cash and bank", "ASSET", "DEBIT"),
            ("2000", "Escrow payable", "LIABILITY", "CREDIT"),
            ("4000", "Commission income", "REVENUE", "CREDIT")]:
        seed("/api/ledger/v1/accounts", "accountCode", {
            "accountCode": code, "name": name, "accountType": atype, "currency": "KES",
            "normalBalance": normal, "status": "ACTIVE"}, f"account {code} {name}")

    print("Payout")
    seed("/api/payout/v1/settlements", "settlementNumber", {
        "settlementNumber": "STL-2026-0001", "payeeOrgId": ORG, "grossAmount": 388000.0,
        "commission": 19400.0, "fees": 1200.0, "netAmount": 367400.0, "currency": "KES",
        "periodStart": day(30), "periodEnd": day(1), "status": "APPROVED"},
        "STL-2026-0001")

    print("Traceability")
    seed("/api/traceability/v1/trace-batches", "batchCode", {
        "batchCode": "TRACE-MZ-0001", "commodityCode": "MAIZE", "farmId": farm,
        "harvestedOn": day(20), "status": "ACTIVE"}, "TRACE-MZ-0001")

    print("Notification")
    seed("/api/notification/v1/notification-templates", "code", {
        "code": "HARVEST_REMINDER", "channel": "SMS", "locale": "en",
        "subject": "Harvest window",
        "body": "Your {{crop}} on {{plot}} reaches harvest in {{days}} days.",
        "active": True, "revision": 1}, "harvest reminder template")

    print("Media")
    seed("/api/media/v1/media-assets", "storageKey", {
        "storageKey": "scouting/2026/njoro-a1-faw.jpg", "contentType": "image/jpeg",
        "sizeBytes": 482113, "width": 1600, "height": 1200, "virusScanned": True,
        "virusClean": True, "status": "READY"}, "scouting photo")

    print("Search")
    seed("/api/search/v1/search-documents", "docId", {
        "indexName": "listings", "docId": "listing-maize-8000", "docType": "SUPPLY_LISTING",
        "title": "8000 kg Grade 1 maize, Nakuru", "body": "Grade 1 maize from Njoro Home Farm.",
        "indexedAt": ts(0), "status": "ACTIVE"}, "listing search document")

    print("Analytics")
    seed("/api/analytics/v1/metric-snapshots", "metricKey", {
        "metricKey": "gmv_kes_daily", "periodStart": ts(1, 0), "periodEnd": ts(0, 0),
        "granularity": "DAY", "value": 388000.0, "computedAt": ts(0, 1)},
        "daily GMV snapshot")

    print("Audit")
    # Written through the append endpoint, not the raw CRUD: the service assigns
    # the sequence and both hashes, so the chain still verifies afterwards.
    if not CHECK_ONLY:
        status, _ = call("GET", "/api/audit/v1/audit-records?size=1")
        if status != 200:
            print("  skip    sign-in audit record  (service not reachable)")
        else:
            status, _ = call("POST", "/api/audit/v1/audit-trail", {
                "serviceName": "identity-service", "actorRole": "ADMIN",
                "action": "USER_SIGNED_IN", "resourceType": "User",
                "resourceId": "demo-user", "outcome": "SUCCESS", "occurredAt": ts(0, 5)})
            print("  appended sign-in audit record"
                  if status in (200, 201) else f"  FAILED  sign-in audit record (HTTP {status})")

    print("Identity")
    seed("/api/identity/v1/organisations", "name", {
        "name": "Green Acres Cooperative", "orgType": "COOPERATIVE", "county": "Nakuru",
        "phone": "+254700000001", "email": "info@greenacres.co.ke",
        "status": "ACTIVE", "kycStatus": "VERIFIED"}, "Green Acres Cooperative")

    print(f"\ncreated {created}   already there {existing}   skipped/failed {skipped}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
