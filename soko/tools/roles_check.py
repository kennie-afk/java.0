import json, sys, urllib.request, urllib.error

API = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8090"
PW = "a-strong-demo-passphrase"

def call(path, body=None, token=None, method=None):
    data = json.dumps(body).encode() if body is not None else None
    h = {"Content-Type": "application/json"}
    if token: h["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{API}{path}", data=data, method=method or ("POST" if data else "GET"), headers=h)
    try:
        with urllib.request.urlopen(req) as r: return json.loads(r.read() or b"{}"), r.status
    except urllib.error.HTTPError as e:
        return {"_body": e.read().decode()[:150]}, e.code

owner, _ = call("/v1/auth/login", {"email": "grace@mazingira.co.ke", "password": PW})
T = owner["accessToken"]

sup, _ = call("/v1/suppliers", token=T)
cus, _ = call("/v1/customers", token=T)
supplier_id, customer_id = sup[0]["id"], cus[0]["id"]
supplier_name, customer_name = sup[0]["name"], cus[0]["name"]

call("/v1/users", {"fullName": "Peter Mwangi", "email": "peter@limuru.co.ke", "password": PW,
                   "role": "SUPPLIER", "supplierId": supplier_id}, T)
call("/v1/users", {"fullName": "Asha Buyer", "email": "asha@zucchini.co.ke", "password": PW,
                   "role": "CUSTOMER", "customerId": customer_id}, T)

st, _ = call("/v1/auth/login", {"email": "peter@limuru.co.ke", "password": PW})
ct, _ = call("/v1/auth/login", {"email": "asha@zucchini.co.ke", "password": PW})
ST, CT = st.get("accessToken"), ct.get("accessToken")

print(f"  supplier account -> {supplier_name}")
print(f"  customer account -> {customer_name}")
print()

my_offers, code = call("/v1/supplier/offers", token=ST)
print(f"  supplier sees own offers        {code}  n={len(my_offers) if isinstance(my_offers,list) else '-'}")
fulfil, code = call("/v1/supplier/fulfilments", token=ST)
print(f"  supplier sees fulfilments       {code}  n={len(fulfil) if isinstance(fulfil,list) else '-'}")

shop, code = call("/v1/shop/products", token=CT)
print(f"  customer sees storefront        {code}  n={len(shop) if isinstance(shop,list) else '-'}")

print()
print("  --- isolation ---")
_, code = call("/v1/orders", token=CT);           print(f"  customer -> operator orders     {code}  (403 expected)")
_, code = call("/v1/offers", token=CT);           print(f"  customer -> operator offers     {code}  (403 expected)")
_, code = call("/v1/shop/products", token=ST);    print(f"  supplier -> storefront          {code}  (401 expected)")
_, code = call("/v1/supplier/offers", token=CT);  print(f"  customer -> supplier portal     {code}  (401 expected)")

print()
print("  --- does the storefront leak cost or margin? ---")
if isinstance(shop, list) and shop:
    keys = sorted(shop[0].keys())
    leaked = [k for k in keys if "cost" in k.lower() or "margin" in k.lower()]
    print(f"  fields: {', '.join(keys)}")
    print(f"  RESULT: {'LEAK - ' + str(leaked) if leaked else 'PASS - no cost or margin exposed'}")

print()
print("  --- customer places an order and tracks it ---")
if isinstance(shop, list) and shop:
    item = shop[0]
    placed, code = call("/v1/shop/orders", {"lines": [{"productId": item["id"], "quantity": 3}]}, CT)
    print(f"  checkout                        {code}  {placed.get('reference','')} total KSh {placed.get('totalCents',0)/100:,.0f}")
    mine, code = call("/v1/shop/orders", token=CT)
    print(f"  customer order history          {code}  n={len(mine) if isinstance(mine,list) else '-'}")
    leaked = [k for k in (mine[0].keys() if isinstance(mine,list) and mine else []) if "cost" in k.lower() or "margin" in k.lower()]
    print(f"  history leaks cost/margin:      {'YES ' + str(leaked) if leaked else 'no'}")

print()
print("  --- supplier dispatches a line ---")
fulfil, _ = call("/v1/supplier/fulfilments", token=ST)
routed = [f for f in fulfil if f["status"] == "ROUTED"] if isinstance(fulfil, list) else []
if routed:
    line = routed[0]
    out, code = call(f"/v1/supplier/fulfilments/{line['lineId']}/dispatch",
                     {"trackingNote": "Loaded on the 06:00 chilled run"}, ST)
    print(f"  dispatch                        {code}  {out.get('status')}")
    out, code = call(f"/v1/supplier/fulfilments/{line['lineId']}/deliver", {}, ST)
    print(f"  deliver                         {code}  {out.get('status')}")
    out, code = call(f"/v1/supplier/fulfilments/{line['lineId']}/dispatch", {"trackingNote": "again"}, ST)
    print(f"  dispatch again (should refuse)  {code}")
else:
    print("  no routed lines for this supplier")
