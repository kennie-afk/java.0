import json
import random
import sys
import urllib.error
import urllib.request

API = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8090"
random.seed(11)

# --- PLACEHOLDER: swap in the client's real business/brand name and contact
# details before presenting. Everything else below is real product/supplier
# data shaped for a fermented-milk drink business. -----------------------
DISTRIBUTOR_ORG = "Fresh Ferment Co. (RENAME ME)"
DISTRIBUTOR_NAME = "Owner Name (RENAME ME)"
DISTRIBUTOR_EMAIL = "owner@freshferment-demo.co.ke"
DEMO_PASSWORD = "a-strong-demo-passphrase"
# Demo logins for the other two sides of the marketplace, so all three roles
# (owner, one customer, one supplier) can actually be signed into and shown,
# not just present as data records the owner's account can see.
DEMO_CUSTOMER_EMAIL = "customer@freshferment-demo.co.ke"
DEMO_SUPPLIER_EMAIL = "supplier@freshferment-demo.co.ke"
# ---------------------------------------------------------------------------


def call(path, body=None, token=None, method=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(
        f"{API}{path}", data=data, method=method or ("POST" if data else "GET"), headers=headers
    )
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        return {"_error": e.code, "_body": e.read().decode()[:200]}


def token_for(org, name, email):
    out = call(
        "/v1/auth/register",
        {"organisationName": org, "fullName": name, "email": email, "password": DEMO_PASSWORD},
    )
    if "_error" in out:
        out = call("/v1/auth/login", {"email": email, "password": DEMO_PASSWORD})
    return out.get("accessToken")


# Suppliers deliberately span the full routing story: two nearby cold-chain
# dairies (always viable), one nearby but no cold chain at all (always
# refused for these products), and one far cold-chain supplier whose lead
# time clears the long-life products but not the one short-life artisanal
# batch — the same "cheap but too slow for this shelf life" shape the
# existing fresh-milk example demonstrates, carried over to fermented milk.
SUPPLIERS = [
    ("Kabete Ferment Dairy", "Kiambu", 5, True, 0.97),
    ("Nyahururu Fresh Cultures", "Laikipia", 7, True, 0.96),
    ("Ol Kalou Cultured Milk", "Nyandarua", 9, True, 0.94),
    ("Eldoret Highlands Creamery", "Uasin Gishu", 26, True, 0.91),
    ("Meru Traditional Mursik Co-op", "Meru", 20, False, 0.90),
]

# Fermented milk keeps longer refrigerated than fresh milk because
# fermentation is itself a preservation process (10-14 days is typical for a
# commercial cultured-milk drink), except for a small-batch, no-additives
# line that is deliberately sold on a much shorter shelf life. Prices are in
# cents (KSh 1 = 100 cents), matching the rest of the platform.
PRODUCTS = [
    ("FML-500", "Fermented milk (mala) 500ml", "Dairy", "bottle", True, True, 240, 7000),
    ("FML-1L", "Fermented milk (mala) 1L", "Dairy", "bottle", True, True, 240, 13000),
    ("FML-STRAW-500", "Strawberry fermented milk 500ml", "Dairy", "bottle", True, True, 216, 9000),
    ("FML-MNG-500", "Mango fermented milk 500ml", "Dairy", "bottle", True, True, 216, 9000),
    ("FML-VAN-500", "Vanilla fermented milk 500ml", "Dairy", "bottle", True, True, 216, 9000),
    ("FML-PRO-500", "Probiotic plus fermented milk 500ml", "Dairy", "bottle", True, True, 288, 11000),
    ("FML-SS-250", "Fermented milk single-serve 250ml", "Dairy", "bottle", True, True, 216, 5000),
    ("FML-ART-500", "Artisanal small-batch mala 500ml, no additives", "Dairy", "bottle", True, True, 24, 12000),
    ("FML-FAM-2L", "Family pack fermented milk 2L", "Dairy", "jug", True, True, 240, 24000),
]

# Buyers who realistically stock a fermented-milk drink line in Kenya:
# supermarkets, health-food shops and gyms/juice bars, not generic grocers.
CUSTOMERS = [
    ("Healthy U Nutrition Shop", "+254700222001", "Nairobi"),
    ("Chandarana Foodplus", "+254700222002", "Nairobi"),
    ("Naivas Kiambu Road", "+254700222003", "Kiambu"),
    ("CrossFit Karen Juice Bar", "+254700222004", "Nairobi"),
    ("Quickmart Nakuru", "+254700222005", "Nakuru"),
]


def seed():
    token = token_for(DISTRIBUTOR_ORG, DISTRIBUTOR_NAME, DISTRIBUTOR_EMAIL)
    if not token:
        print("could not authenticate the distributor account")
        return

    supplier_ids = []
    for s in SUPPLIERS:
        out = call(
            "/v1/suppliers",
            {"name": s[0], "county": s[1], "leadTimeHours": s[2], "coldChain": s[3], "reliability": s[4]},
            token,
        )
        if "id" in out:
            supplier_ids.append((out["id"], s))

    product_ids = []
    for p in PRODUCTS:
        out = call(
            "/v1/products",
            {
                "sku": p[0], "name": p[1], "category": p[2], "unit": p[3],
                "perishable": p[4], "requiresColdChain": p[5],
                "shelfLifeHours": p[6], "listPriceCents": p[7],
            },
            token,
        )
        if "id" in out:
            product_ids.append((out["id"], p))

    # The artisanal batch is deliberately offered ONLY by the two suppliers who
    # cannot legally fulfil it (no cold chain / lead time exceeds its 24h shelf
    # life), so ordering it live during the demo produces a real, unroutable
    # order with the exact refusal reasons in the response -- not a happy-path
    # order that never surfaces why the routing engine exists.
    UNROUTABLE_DEMO_SKU = "FML-ART-500"
    UNROUTABLE_DEMO_SUPPLIERS = {"Eldoret Highlands Creamery", "Meru Traditional Mursik Co-op"}

    offers = 0
    for pid, p in product_ids:
        for sid, s in supplier_ids:
            if p[0] == UNROUTABLE_DEMO_SKU and s[0] not in UNROUTABLE_DEMO_SUPPLIERS:
                continue
            # Every supplier gets a chance to quote on every product, on purpose:
            # the routing demo should show real suppliers being refused by the
            # engine's own rules, not just being absent from the offer list.
            if random.random() < 0.8:
                margin = random.uniform(0.55, 0.80)
                out = call(
                    "/v1/offers",
                    {
                        "supplierId": sid, "productId": pid,
                        "costCents": int(p[7] * margin),
                        "availableQty": random.randint(50, 400),
                    },
                    token,
                )
                if "id" in out:
                    offers += 1

    customer_ids = []
    for c in CUSTOMERS:
        out = call("/v1/customers", {"name": c[0], "phone": c[1], "county": c[2]}, token)
        if "id" in out:
            customer_ids.append(out["id"])

    # Give the first customer and the first supplier an actual login, not just
    # a data record -- otherwise nobody can sign in and show the storefront or
    # the supplier portal, only the owner's own console.
    if customer_ids:
        call(
            "/v1/users",
            {
                "fullName": "Demo Customer", "email": DEMO_CUSTOMER_EMAIL,
                "password": DEMO_PASSWORD, "role": "CUSTOMER",
                "customerId": customer_ids[0], "supplierId": None,
            },
            token,
        )
    if supplier_ids:
        call(
            "/v1/users",
            {
                "fullName": "Demo Supplier", "email": DEMO_SUPPLIER_EMAIL,
                "password": DEMO_PASSWORD, "role": "SUPPLIER",
                "supplierId": supplier_ids[0][0], "customerId": None,
            },
            token,
        )

    # A curated set of orders, not random ones: every sellable product appears
    # in at least one order, so the console has something to show for each
    # line in her actual catalogue. The artisanal batch is deliberately left
    # unordered here -- it has no viable supplier by design (see above), so
    # ordering it is the live moment during the demo: try it on stage and the
    # platform visibly refuses it, with the real reason, instead of a
    # pre-baked failure sitting in a list nobody triggered.
    placed = unroutable = 0
    for pid, p in product_ids:
        if p[0] == UNROUTABLE_DEMO_SKU:
            continue
        cid = random.choice(customer_ids)
        out = call("/v1/orders", {"customerId": cid, "lines": [{"productId": pid, "quantity": random.randint(4, 24)}]}, token)
        if "_error" in out:
            unroutable += 1
        else:
            placed += 1

    sellable_product_ids = [(pid, p) for pid, p in product_ids if p[0] != UNROUTABLE_DEMO_SKU]

    for _ in range(15):
        cid = random.choice(customer_ids)
        lines = [
            {"productId": pid, "quantity": random.randint(2, 20)}
            for pid, _ in random.sample(sellable_product_ids, random.randint(1, min(4, len(sellable_product_ids))))
        ]
        out = call("/v1/orders", {"customerId": cid, "lines": lines}, token)
        if "_error" in out:
            unroutable += 1
        else:
            placed += 1

    ov = call("/v1/overview", token=token)
    print(f"Seeded {DISTRIBUTOR_ORG}")
    print(f"  owner console: {DISTRIBUTOR_EMAIL} / {DEMO_PASSWORD}")
    print(f"  customer storefront: {DEMO_CUSTOMER_EMAIL} / {DEMO_PASSWORD}")
    print(f"  supplier portal: {DEMO_SUPPLIER_EMAIL} / {DEMO_PASSWORD}")
    print(
        f"  {len(supplier_ids)} suppliers, {len(product_ids)} products, {offers} offers, "
        f"{len(customer_ids)} customers, {placed} orders placed, {unroutable} unroutable"
    )
    print(
        f"  revenue KSh {ov.get('revenueCents', 0) / 100:,.0f}  "
        f"margin KSh {ov.get('marginCents', 0) / 100:,.0f} ({ov.get('marginPercent')}%)"
    )
    print()
    print("Live moment to trigger ON STAGE, not pre-seeded:")
    print("  Order 'Artisanal small-batch mala 500ml, no additives' from the storefront.")
    print("  Its only two quoting suppliers are Meru Traditional Mursik Co-op (no cold")
    print("  chain at all) and Eldoret Highlands Creamery (26h out, but the product's")
    print("  shelf life is only 24h) -- so the platform visibly REFUSES the order and")
    print("  says exactly why, live, instead of silently failing or guessing.")
    print()
    print("  Every other product on the catalogue already has a placed order, routed")
    print("  through whichever nearby cold-chain supplier was cheapest.")


if __name__ == "__main__":
    seed()
