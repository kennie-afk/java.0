"""Proves the M-Pesa STK push + platform billing flow end to end against a
live instance, the same way oversell_check.py proves stock and roles_check.py
proves role isolation -- assertions against real HTTP responses, not a
description of what the code is supposed to do.
"""
import json, sys, time, urllib.request, urllib.error

API = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8090"
PW = "a-strong-demo-passphrase"


def call(path, body=None, token=None, method=None):
    data = json.dumps(body).encode() if body is not None else None
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{API}{path}", data=data, method=method or ("POST" if data else "GET"), headers=h)
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read() or b"{}"), r.status
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        try:
            return json.loads(body), e.code
        except json.JSONDecodeError:
            return {"_body": body[:200]}, e.code


def check(label, cond):
    print(f"  {'PASS' if cond else 'FAIL'} - {label}")
    if not cond:
        sys.exit(1)


owner, _ = call("/v1/auth/login", {"email": "grace@mazingira.co.ke", "password": PW})
T = owner["accessToken"]

customers, _ = call("/v1/customers", token=T)
products, _ = call("/v1/products", token=T)
customer_id = customers[0]["id"]

# A customer pays for their OWN order through /v1/shop, never through the
# owner-facing /v1/orders -- so this needs a real CUSTOMER-role login, not
# the owner's token, exactly as SecurityConfig enforces in production.
buyer_email = f"buyer-{customer_id[:8]}@mazingira.co.ke"
call("/v1/users", {"fullName": "Buyer", "email": buyer_email, "password": PW,
                    "role": "CUSTOMER", "customerId": customer_id}, T)
buyer, _ = call("/v1/auth/login", {"email": buyer_email, "password": PW})
CT = buyer["accessToken"]

print("--- order payment (M-Pesa STK push, mock mode) ---")
checkout, code = call("/v1/shop/orders", {"lines": [{"productId": products[0]["id"], "quantity": 2}]}, CT)
check("customer places their own order", code == 201)
order_id = checkout["orderId"]

pay1, code = call(f"/v1/shop/orders/{order_id}/pay", {"msisdn": "254712345678"}, CT)
check("STK push accepted (202)", code == 202)
check("payment is PENDING (mock waits for a callback)", pay1["status"] == "PENDING")
checkout_id = pay1["checkoutRequestId"]

pay2, code = call(f"/v1/shop/orders/{order_id}/pay", {"msisdn": "254712345678"}, CT)
check("re-tapping pay before completion returns the SAME push, not a second one",
      pay2["checkoutRequestId"] == checkout_id)

callback_body = {
    "Body": {"stkCallback": {
        "MerchantRequestID": pay1.get("merchantRequestId") or "mock",
        "CheckoutRequestID": checkout_id,
        "ResultCode": 0,
        "ResultDesc": "The service request is processed successfully.",
        "CallbackMetadata": {"Item": [
            {"Name": "Amount", "Value": checkout["totalCents"] / 100},
            {"Name": "MpesaReceiptNumber", "Value": "NLJ7RT61SV"},
            {"Name": "PhoneNumber", "Value": 254712345678},
        ]},
    }}
}
cb1, code = call("/v1/public/mpesa/stk-callback", callback_body, method="POST")
check("callback accepted (200)", code == 200)

order_after, _ = call(f"/v1/orders/{order_id}", token=T)
check("order status flips to PAID", order_after["status"] == "PAID")

cb2, code = call("/v1/public/mpesa/stk-callback", callback_body, method="POST")
check("replayed callback still returns 200 (Safaricom retries until it sees one)", code == 200)

print()
print("--- the same success callback arriving 16 times at once settles ONCE ---")
import concurrent.futures
race_order, code = call("/v1/shop/orders", {"lines": [{"productId": products[0]["id"], "quantity": 1}]}, CT)
check("customer places a second order", code == 201)
race_pay, _ = call(f"/v1/shop/orders/{race_order['orderId']}/pay", {"msisdn": "254712345678"}, CT)
race_body = json.loads(json.dumps(callback_body))
race_body["Body"]["stkCallback"]["CheckoutRequestID"] = race_pay["checkoutRequestId"]
race_body["Body"]["stkCallback"]["CallbackMetadata"]["Item"][1]["Value"] = "NLJRACE001"
with concurrent.futures.ThreadPoolExecutor(16) as pool:
    codes = list(pool.map(lambda _: call("/v1/public/mpesa/stk-callback", race_body, method="POST")[1], range(16)))
check("every delivery is answered 200", set(codes) == {200})
ledger, _ = call("/v1/billing/ledger?limit=500", token=T)
received = [e for e in ledger if e["referenceId"] == race_order["orderId"] and e["type"] == "PAYMENT_RECEIVED"]
check("exactly one PAYMENT_RECEIVED ledger entry for the order (not 16)", len(received) == 1)
race_after, _ = call(f"/v1/orders/{race_order['orderId']}", token=T)
check("the order is PAID", race_after["status"] == "PAID")

print()
print("--- order cancel restocks and voids the commission ---")
order2, _ = call("/v1/orders", {"customerId": customer_id,
                                  "lines": [{"productId": products[1]["id"], "quantity": 3}]}, T)
offers_before, _ = call("/v1/offers", token=T)
routed_line = order2["lines"][0]
offer_row = next(o for o in offers_before
                  if o["product"] == routed_line["productName"] and o["supplier"] == routed_line["supplierName"])
qty_before = offer_row["availableQty"]

cancel, code = call(f"/v1/orders/{order2['orderId']}/cancel", {"reason": "customer changed their mind"}, T)
check("cancel accepted", code == 200 and cancel["status"] == "CANCELLED")

offers_after, _ = call("/v1/offers", token=T)
offer_row_after = next(o for o in offers_after if o["id"] == offer_row["id"])
check("cancelling restocks the offer", offer_row_after["availableQty"] == qty_before + 3)

ledger, _ = call("/v1/billing/ledger?limit=500", token=T)
voided = [e for e in ledger if e["referenceId"] == order2["orderId"] and e["type"] == "COMMISSION_VOIDED"]
check("a COMMISSION_VOIDED ledger entry exists for the cancelled order", len(voided) == 1)
check("the voiding entry is a negative offset, not an edit of the original", voided[0]["amountCents"] < 0)

cancel_paid, code = call(f"/v1/orders/{order_id}/cancel", {}, T)
check("a PAID order cannot be cancelled through this endpoint", code == 400)

print()
print("--- subscription, commission accrual, invoicing, ledger reconciliation ---")
sub, _ = call("/v1/billing/subscription", token=T)
if sub["plan"] != "FREE":
    # tools/seed_history.py leaves the demo tenant on GROWTH; put it back so the arithmetic below holds.
    call("/v1/billing/subscription/plan", {"plan": "FREE"}, T, method="PUT")
    sub, _ = call("/v1/billing/subscription", token=T)
check("tenant is on FREE before the test changes plan", sub["plan"] == "FREE")

changed, code = call("/v1/billing/subscription/plan", {"plan": "GROWTH"}, T, method="PUT")
check("owner can change plan", code == 200 and changed["plan"] == "GROWTH")

order3, _ = call("/v1/orders", {"customerId": customer_id,
                                  "lines": [{"productId": products[2]["id"], "quantity": 1}]}, T)
commission_expected = order3["revenueCents"] * 350 // 10000  # GROWTH plan bps

period = {"periodStart": "2020-01-01T00:00:00Z", "periodEnd": "2030-01-01T00:00:00Z"}
invoice, code = call("/v1/billing/invoices/generate", period, T)
check("invoice generated", code == 201)
check("invoice includes GROWTH's monthly fee", invoice["subscriptionFeeCents"] == 299900)
check("invoice includes at least this order's commission",
      invoice["commissionCents"] >= commission_expected)

ledger_now, _ = call("/v1/billing/ledger?limit=500", token=T)
ledger_for_invoice = [e for e in ledger_now if e["referenceId"] == invoice["id"]]
ledger_sum = sum(e["amountCents"] for e in ledger_for_invoice
                  if e["type"] in ("SUBSCRIPTION_FEE", "COMMISSION_INVOICED"))
check("ledger entries for this invoice sum to exactly its total (the reconciliation proof)",
      ledger_sum == invoice["totalCents"])

invoice_pay, code = call(f"/v1/billing/invoices/{invoice['id']}/pay", {"msisdn": "254712345678"}, T)
check("invoice payment initiated", code == 202)
invoice_checkout = invoice_pay["checkoutRequestId"]

invoice_callback = {
    "Body": {"stkCallback": {
        "MerchantRequestID": "mock", "CheckoutRequestID": invoice_checkout, "ResultCode": 0,
        "ResultDesc": "ok",
        "CallbackMetadata": {"Item": [
            {"Name": "MpesaReceiptNumber", "Value": "NLJ7RT61SW"},
        ]},
    }}
}
call("/v1/public/mpesa/stk-callback", invoice_callback, method="POST")
invoice_after, _ = call("/v1/billing/invoices?limit=10", token=T)
paid = next(i for i in invoice_after if i["id"] == invoice["id"])
check("invoice flips to PAID after its STK callback", paid["status"] == "PAID")

print()
print("--- wastage ---")
offer_for_wastage = offers_before[0]
before, _ = call("/v1/wastage", token=T)
record, code = call("/v1/offers", token=T)  # refresh
target_offer = next(o for o in record if o["id"] == offer_for_wastage["id"])
qty_available = target_offer["availableQty"]

wastage, code = call("/v1/wastage", {"offerId": offer_for_wastage["id"], "quantity": 1, "reason": "expired"}, T)
check("wastage recorded", code == 201 and wastage["quantity"] == 1)

after, _ = call("/v1/wastage", token=T)
check("wastage total value increases", after["totalValueCents"] > before["totalValueCents"])

offers_final, _ = call("/v1/offers", token=T)
target_after = next(o for o in offers_final if o["id"] == offer_for_wastage["id"])
check("wastage removes stock from what's sellable", target_after["availableQty"] == qty_available - 1)

print()
print("--- product/supplier update (the documented \"no edit\" gap) ---")
product_id = products[0]["id"]
updated, code = call(f"/v1/products/{product_id}",
                       {"photoUrl": "https://example.test/milk.jpg"}, T, method="PATCH")
check("product photo can now be set", code == 200 and updated["photoUrl"] is not None)

suppliers, _ = call("/v1/suppliers", token=T)
supplier_id = suppliers[0]["id"]
deactivated, code = call(f"/v1/suppliers/{supplier_id}", {"status": "INACTIVE"}, T, method="PATCH")
check("a supplier can now be deactivated", code == 200 and deactivated["status"] == "INACTIVE")

print()
print("ALL MONETIZATION CHECKS PASSED")
