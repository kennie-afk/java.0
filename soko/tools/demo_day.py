"""Plays one order end to end through all three sides, pausing so you can show each screen.

    python3 tools/demo_day.py http://localhost:8090 http://localhost:3500        # pauses for Enter
    python3 tools/demo_day.py http://localhost:8090 http://localhost:3500 --auto  # 4 s between steps

Needs tools/seed.py and tools/seed_history.py to have run (it uses the demo customer and supplier
logins). With SOKO_MPESA_MOCK_AUTOCOMPLETE_SECONDS set the mock customer answers the M-Pesa prompt
itself; otherwise this script posts the callback a phone would have triggered.
"""
import json, sys, time, urllib.error, urllib.request

API = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else "http://127.0.0.1:8090"
CONSOLE = sys.argv[2] if len(sys.argv) > 2 and not sys.argv[2].startswith("--") else "http://127.0.0.1:3500"
AUTO = "--auto" in sys.argv
PW = "a-strong-demo-passphrase"


def call(path, body=None, token=None, method=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{API}{path}", data=data, method=method or ("POST" if data is not None else "GET"), headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read() or b"{}"), r.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return json.loads(raw), e.code
        except json.JSONDecodeError:
            return {"_body": raw[:200]}, e.code


def pause(message, where):
    print(f"\n>> {message}\n   show: {CONSOLE}{where}")
    if AUTO:
        time.sleep(4)
    else:
        input("   press Enter to continue ")


def login(email):
    out, code = call("/v1/auth/login", {"email": email, "password": PW})
    if code != 200:
        sys.exit(f"cannot sign in as {email} ({code}); run tools/seed.py and tools/seed_history.py first")
    return out["accessToken"]


shop = login("customer@mazingira.co.ke")
supplier = login("supplier@mazingira.co.ke")
owner = login("grace@mazingira.co.ke")

products, _ = call("/v1/shop/products", token=shop)
by_name = {p["name"]: p for p in products}
want = [p for n, p in by_name.items() if "milk 1L" in n or "yoghurt" in n] or products[:2]

pause("A shop (Zucchini Greengrocers) opens the storefront and fills a basket", "/shop")
order, code = call("/v1/shop/orders", {"lines": [{"productId": p["id"], "quantity": 24} for p in want]}, shop)
if code != 201:
    sys.exit(f"order refused: {order}")
print(f"   order {order.get('reference')} placed, total KSh {order['totalCents'] / 100:,.0f}")
pause("The order was routed line by line. Open the order as the distributor and read the routing reasons", f"/orders/{order['orderId']}")

pay, code = call(f"/v1/shop/orders/{order['orderId']}/pay", {"msisdn": "254712345678"}, shop)
print(f"   STK push {pay.get('status')} ({code}); the customer approves the prompt on their phone")
for _ in range(8):
    time.sleep(1)
    detail, _ = call(f"/v1/orders/{order['orderId']}", token=owner)
    if detail.get("status") == "PAID":
        break
else:
    call("/v1/public/mpesa/stk-callback", {"Body": {"stkCallback": {
        "MerchantRequestID": "demo", "CheckoutRequestID": pay["checkoutRequestId"], "ResultCode": 0, "ResultDesc": "ok",
        "CallbackMetadata": {"Item": [{"Name": "MpesaReceiptNumber", "Value": "DEMODAY01"}]}}}}, method="POST")
    detail, _ = call(f"/v1/orders/{order['orderId']}", token=owner)
print(f"   order is now {detail.get('status')}")
pause("Paid. The commission accrued to the platform ledger the moment the order was routed", "/billing")

mine, _ = call("/v1/supplier/fulfilments?status=ROUTED&limit=50", token=supplier)
lines = [l for l in mine if l["reference"] == order["reference"]]
pause(f"Each supplier sees only its own lines. Limuru Dairy has {len(lines)} of this order waiting", "/supplier")
for line in lines:
    call(f"/v1/supplier/fulfilments/{line['lineId']}/dispatch", {"trackingNote": "Chilled van KBX 421T, leaving Limuru 06:40"}, supplier)
pause("Dispatched with a tracking note. The shop's order page shows the progress", "/shop/orders")
for line in lines:
    call(f"/v1/supplier/fulfilments/{line['lineId']}/deliver", {}, supplier)
pause("Delivered. Open the receipt: it shows which supplier filled each line", f"/orders/{order['orderId']}/receipt")

cancel, code = call(f"/v1/orders/{order['orderId']}/cancel", {"reason": "demo"}, owner)
print(f"\nA paid order cannot be cancelled: HTTP {code} ({cancel.get('detail') or cancel.get('title')})")
print("\nDone. The order stays in the books as history.")
