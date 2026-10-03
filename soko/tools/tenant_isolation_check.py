"""Two tenants, one database: tenant A tries to read, change and reference tenant B's records
through the real API. Every attempt must fail as 'not found' (404) or a refusal (4xx), never
succeed and never leak that the record exists.

Needs a seeded stack (python3 tools/seed.py). Run:  python3 tools/tenant_isolation_check.py
"""
import json, sys, urllib.request, urllib.error

API = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8090"
PW = "a-strong-demo-passphrase"


def call(path, body=None, token=None, method=None):
    data = json.dumps(body).encode() if body is not None else None
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(API + path, data=data, method=method or ("POST" if data is not None else "GET"), headers=h)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return (json.loads(raw) if raw else {}), r.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return json.loads(raw), e.code
        except json.JSONDecodeError:
            return {"_": raw[:100]}, e.code


failed = 0


def check(label, ok):
    global failed
    print(f"  {'PASS' if ok else 'FAIL'} - {label}")
    failed += 0 if ok else 1


A = call("/v1/auth/login", {"email": "grace@mazingira.co.ke", "password": PW})[0]["accessToken"]
B = call("/v1/auth/login", {"email": "daniel@riftfresh.co.ke", "password": PW})[0]["accessToken"]
b_sup = call("/v1/suppliers", token=B)[0]
b_prod = call("/v1/products", token=B)[0]
b_cust = call("/v1/customers", token=B)[0]
b_ord = call("/v1/orders?limit=3", token=B)[0]
b_ord = b_ord if isinstance(b_ord, list) else b_ord.get("items", [])
b_off = call("/v1/offers", token=B)[0]
a_prod = call("/v1/products", token=A)[0]
a_cust = call("/v1/customers", token=A)[0]
a_sup = call("/v1/suppliers", token=A)[0]
check("tenant B has data to attack", all([b_sup, b_prod, b_cust, b_ord, b_off]))

print("--- reading and changing another tenant's records ---")
check("read B's order by id -> 404", call(f"/v1/orders/{b_ord[0]['id']}", token=A)[1] == 404)
check("cancel B's order -> 404", call(f"/v1/orders/{b_ord[0]['id']}/cancel", {"reason": "x"}, A)[1] == 404)
check("patch B's supplier -> 404", call(f"/v1/suppliers/{b_sup[0]['id']}", {"status": "INACTIVE"}, A, "PATCH")[1] == 404)
check("patch B's product -> 404", call(f"/v1/products/{b_prod[0]['id']}", {"name": "hax"}, A, "PATCH")[1] == 404)
check("B's supplier is untouched", call("/v1/suppliers", token=B)[0][0]["status"] == b_sup[0]["status"])

print("--- referencing another tenant's records from your own ---")
line = [{"productId": b_prod[0]["id"], "quantity": 1}]
check("order for B's customer -> 4xx", 400 <= call("/v1/orders", {"customerId": b_cust[0]["id"], "lines": line}, A)[1] < 500)
check("own customer, B's product -> 4xx", 400 <= call("/v1/orders", {"customerId": a_cust[0]["id"], "lines": line}, A)[1] < 500)
check("offer: own supplier, B's product -> 4xx",
      400 <= call("/v1/offers", {"supplierId": a_sup[0]["id"], "productId": b_prod[0]["id"], "costCents": 100, "availableQty": 5}, A)[1] < 500)
check("offer: B's supplier, own product -> 4xx",
      400 <= call("/v1/offers", {"supplierId": b_sup[0]["id"], "productId": a_prod[0]["id"], "costCents": 100, "availableQty": 5}, A)[1] < 500)
check("account bound to B's customer -> 4xx",
      400 <= call("/v1/users", {"fullName": "x", "email": "iso-x@a.test", "password": PW, "role": "CUSTOMER", "customerId": b_cust[0]["id"]}, A)[1] < 500)
check("wastage on B's offer -> 4xx", 400 <= call("/v1/wastage", {"offerId": b_off[0]["id"], "quantity": 1, "reason": "SPOILED"}, A)[1] < 500)

print("--- lists never mix tenants ---")
ids = lambda rows: {r["id"] for r in rows}
check("supplier lists are disjoint", not ids(call("/v1/suppliers", token=A)[0]) & ids(call("/v1/suppliers", token=B)[0]))
check("product lists are disjoint", not ids(call("/v1/products", token=A)[0]) & ids(call("/v1/products", token=B)[0]))
la = {e["referenceId"] for e in call("/v1/billing/ledger?limit=500", token=A)[0]}
lb = {e["referenceId"] for e in call("/v1/billing/ledger?limit=500", token=B)[0]}
check("ledgers are disjoint", not la & lb)

print("--- operations surface ---")
check("owner can read actuator metrics", call("/actuator/metrics", token=A)[1] == 200)
check("anonymous cannot", call("/actuator/metrics")[1] == 401)

print("--- malformed input is the caller's error, not ours ---")
check("a bad id in the path -> 400, not 500", call("/v1/orders/not-a-uuid", token=A)[1] == 400)
bad = urllib.request.Request(API + "/v1/products", data=b"{not json", method="POST",
                             headers={"Content-Type": "application/json", "Authorization": "Bearer " + A})
try:
    code = urllib.request.urlopen(bad).status
except urllib.error.HTTPError as e:
    code = e.code
check("malformed JSON -> 400, not 500", code == 400)
dup = {"sku": "ISO-DUP", "name": "dup", "category": "Dairy", "unit": "packet", "perishable": False,
       "requiresColdChain": False, "shelfLifeHours": 720, "listPriceCents": 100}
call("/v1/products", dup, A)
check("a repeated SKU -> 409, not 500", call("/v1/products", dup, A)[1] == 409)

print()
print("ALL TENANT ISOLATION CHECKS PASSED" if not failed else f"{failed} CHECK(S) FAILED")
sys.exit(1 if failed else 0)
