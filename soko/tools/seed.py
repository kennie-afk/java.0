import json, random, sys, urllib.request, urllib.error

API = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8090"
random.seed(11)

def call(path, body=None, token=None, method=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{API}{path}", data=data, method=method or ("POST" if data else "GET"), headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        return {"_error": e.code, "_body": e.read().decode()[:200]}

def token_for(org, name, email):
    out = call("/v1/auth/register", {"organisationName": org, "fullName": name,
                                     "email": email, "password": "a-strong-demo-passphrase"})
    if "_error" in out:
        out = call("/v1/auth/login", {"email": email, "password": "a-strong-demo-passphrase"})
    return out.get("accessToken")

SUPPLIERS = [
    ("Limuru Dairy Cooperative", "Kiambu", 6, True, 0.97),
    ("Nakuru Milk Collectors", "Nakuru", 10, True, 0.93),
    ("Kinangop Highland Farms", "Nyandarua", 14, False, 0.90),
    ("Githunguri Chilled Logistics", "Kiambu", 4, True, 0.98),
    ("Rift Valley Produce Union", "Nakuru", 30, False, 0.88),
]

PRODUCTS = [
    ("MLK-500", "Fresh milk 500ml", "Dairy", "packet", True, True, 48, 6500),
    ("MLK-1L", "Fresh milk 1L", "Dairy", "packet", True, True, 48, 12000),
    ("YOG-500", "Natural yoghurt 500ml", "Dairy", "tub", True, True, 168, 18000),
    ("CHE-250", "Cottage cheese 250g", "Dairy", "tub", True, True, 240, 32000),
    ("BUT-250", "Farm butter 250g", "Dairy", "block", True, True, 720, 28000),
    ("EGG-30", "Tray of eggs (30)", "Poultry", "tray", True, False, 336, 45000),
    ("POT-50", "Potatoes 50kg", "Produce", "bag", False, False, 720, 250000),
    ("KAL-1", "Kale bunch", "Produce", "bunch", True, False, 72, 3000),
    ("TOM-25", "Tomatoes 25kg", "Produce", "crate", True, False, 168, 180000),
    ("AVO-20", "Avocados 20kg", "Produce", "crate", False, False, 336, 160000),
]

CUSTOMERS = [
    ("Zucchini Greengrocers", "+254700111001", "Nairobi"),
    ("Chandarana Foodplus", "+254700111002", "Nairobi"),
    ("Naivas Kiambu Road", "+254700111003", "Kiambu"),
    ("Quickmart Nakuru", "+254700111004", "Nakuru"),
    ("Tuskys Nyandarua", "+254700111005", "Nyandarua"),
]

def seed_tenant(org, name, email, label):
    token = token_for(org, name, email)
    if not token:
        print(f"  {label}: could not authenticate"); return
    supplier_ids = []
    for s in SUPPLIERS:
        out = call("/v1/suppliers", {"name": s[0], "county": s[1], "leadTimeHours": s[2],
                                     "coldChain": s[3], "reliability": s[4]}, token)
        if "id" in out: supplier_ids.append((out["id"], s))
    product_ids = []
    for p in PRODUCTS:
        out = call("/v1/products", {"sku": p[0], "name": p[1], "category": p[2], "unit": p[3],
                                    "perishable": p[4], "requiresColdChain": p[5],
                                    "shelfLifeHours": p[6], "listPriceCents": p[7]}, token)
        if "id" in out: product_ids.append((out["id"], p))

    offers = 0
    for pid, p in product_ids:
        for sid, s in supplier_ids:
            if random.random() < 0.65:
                margin = random.uniform(0.60, 0.85)
                out = call("/v1/offers", {"supplierId": sid, "productId": pid,
                                          "costCents": int(p[7] * margin),
                                          "availableQty": random.randint(40, 600)}, token)
                if "id" in out: offers += 1

    customer_ids = []
    for c in CUSTOMERS:
        out = call("/v1/customers", {"name": c[0], "phone": c[1], "county": c[2]}, token)
        if "id" in out: customer_ids.append(out["id"])

    placed = unroutable = 0
    for _ in range(40):
        cid = random.choice(customer_ids)
        lines = [{"productId": pid, "quantity": random.randint(2, 30)}
                 for pid, _ in random.sample(product_ids, random.randint(1, 4))]
        out = call("/v1/orders", {"customerId": cid, "lines": lines}, token)
        if "_error" in out: unroutable += 1
        else: placed += 1

    ov = call("/v1/overview", token=token)
    print(f"  {label}: {len(supplier_ids)} suppliers, {len(product_ids)} products, {offers} offers, "
          f"{len(customer_ids)} customers, {placed} orders placed, {unroutable} unroutable")
    print(f"      revenue KSh {ov.get('revenueCents',0)/100:,.0f}  margin KSh {ov.get('marginCents',0)/100:,.0f} "
          f"({ov.get('marginPercent')}%)")
    return token

if __name__ == "__main__":
    seed_tenant("Mazingira Fresh Distributors", "Grace Wanjiku", "grace@mazingira.co.ke", "Mazingira")
    seed_tenant("Rift Fresh Logistics", "Daniel Kiprop", "daniel@riftfresh.co.ke", "Rift Fresh")
    seed_tenant("Coast Dairy Direct", "Amina Said", "amina@coastdairy.co.ke", "Coast Dairy")
