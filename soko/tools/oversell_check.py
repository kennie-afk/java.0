import json, sys, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor

API = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8090"

def call(path, body=None, token=None, method=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token: headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{API}{path}", data=data, method=method or ("POST" if data else "GET"), headers=headers)
    try:
        with urllib.request.urlopen(req) as r: return json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        return {"_status": e.code}

token = call("/v1/auth/login", {"email": "grace@mazingira.co.ke", "password": "a-strong-demo-passphrase"})["accessToken"]
customer = call("/v1/customers", token=token)[0]["id"]

product = call("/v1/products", {"sku": "SCARCE-1", "name": "Scarce test milk", "category": "Dairy",
    "unit": "packet", "perishable": False, "requiresColdChain": False,
    "shelfLifeHours": 720, "listPriceCents": 10000}, token)
supplier = call("/v1/suppliers", {"name": "Solo Supplier", "county": "Nakuru",
    "leadTimeHours": 6, "coldChain": True, "reliability": 0.99}, token)
STOCK = 50
call("/v1/offers", {"supplierId": supplier["id"], "productId": product["id"],
                    "costCents": 5000, "availableQty": STOCK}, token)

ATTEMPTS, QTY = 120, 1
def attempt(_):
    out = call("/v1/orders", {"customerId": customer,
                              "lines": [{"productId": product["id"], "quantity": QTY}]}, token)
    return "_status" not in out

with ThreadPoolExecutor(max_workers=32) as pool:
    results = list(pool.map(attempt, range(ATTEMPTS)))

accepted = sum(results)
remaining = [o for o in call("/v1/offers", token=token) if o["product"] == "Scarce test milk"][0]["availableQty"]

print(f"  stock at start:      {STOCK}")
print(f"  concurrent attempts: {ATTEMPTS} (32 threads, {QTY} unit each)")
print(f"  accepted:            {accepted}")
print(f"  rejected:            {ATTEMPTS - accepted}")
print(f"  stock remaining:     {remaining}")
print(f"  accepted + remaining = {accepted + remaining}  (must equal {STOCK})")
print(f"  RESULT: {'PASS - no overselling' if accepted + remaining == STOCK and accepted <= STOCK else 'FAIL - OVERSOLD'}")
