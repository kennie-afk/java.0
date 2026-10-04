"""Turns the flat seed (every order placed "just now") into eight weeks of trading history, so the
revenue trend, wastage, billing and ledger screens have something to show.

It goes through the real API wherever the API can do the job (cancellations, wastage, the plan
change, invoice generation, M-Pesa payment), and only back-dates timestamps with SQL, because no
API lets a caller choose when an order was placed. Demo tooling only.

    python3 tools/seed_history.py http://localhost:8090
    SOKO_PSQL="docker exec -i soko-demo-postgres-1 psql -U soko -d soko" python3 tools/seed_history.py ...

Idempotent: a tenant that already has a billing invoice is left alone.
"""
import json, os, random, subprocess, sys, urllib.error, urllib.request
from datetime import datetime, timedelta, timezone

API = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8090"
PSQL = os.environ.get("SOKO_PSQL", "docker compose exec -T postgres psql -U soko -d soko").split()
PW = "a-strong-demo-passphrase"
random.seed(23)
WEEKS = 8


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


def sql(statement):
    out = subprocess.run(PSQL + ["-q", "-t", "-A", "-c", statement], capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(f"psql failed: {out.stderr.strip()}\n(set SOKO_PSQL to a working psql command)")
    return out.stdout.strip()


def iso(dt):
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def history_for(email, label, upgrade_to):
    login, code = call("/v1/auth/login", {"email": email, "password": PW})
    if code != 200:
        print(f"  {label}: cannot sign in ({code}); run tools/seed.py first")
        return
    token = login["accessToken"]
    if call("/v1/billing/invoices?limit=1", token=token)[0]:
        print(f"  {label}: history already present, skipping")
        return
    tenant = sql(f"select tenant_id from users where email = '{email}' and status = 'ACTIVE'")

    orders, _ = call("/v1/orders?limit=200", token=token)
    live = [o for o in orders if o["status"] == "ROUTED"]
    # A realistic cancellation rate: about one order in ten.
    cancelled = 0
    for order in random.sample(live, max(1, len(live) // 10)):
        _, status = call(f"/v1/orders/{order['id']}/cancel", {"reason": random.choice(
            ["Customer changed their mind", "Delivery window missed", "Duplicate order"])}, token)
        cancelled += status == 200

    offers, _ = call("/v1/offers?limit=200", token=token)
    perishable = [o for o in offers if o["coldChain"]] or offers
    wasted = 0
    for offer in random.sample(perishable, min(10, len(perishable))):
        body = {"offerId": offer["id"], "quantity": random.randint(2, 14),
                "reason": random.choice(["EXPIRED", "EXPIRED", "SPOILED", "DAMAGED"])}
        wasted += call("/v1/wastage", body, token)[1] == 201

    # Spread orders over the eight weeks, denser towards the present (a growing business).
    sql(f"""
      with ranked as (
        select id, row_number() over (order by placed_at) as rn, count(*) over () as total
          from orders where tenant_id = '{tenant}'
      )
      update orders o set placed_at = now() - (power(1 - (r.rn::float / (r.total + 1)), 1.45) * interval '{WEEKS * 7} days')
                                              - (random() * interval '9 hours')
        from ranked r where o.id = r.id;
      update platform_commissions c set created_at = o.placed_at from orders o where c.order_id = o.id and c.tenant_id = '{tenant}';
      update platform_ledger l set created_at = o.placed_at
        from orders o where l.reference_type = 'ORDER' and l.reference_id = o.id and l.tenant_id = '{tenant}'
         and l.entry_type in ('COMMISSION');
      update platform_ledger l set created_at = o.cancelled_at
        from orders o where l.reference_type = 'ORDER' and l.reference_id = o.id and l.tenant_id = '{tenant}'
         and l.entry_type = 'COMMISSION_VOIDED' and o.cancelled_at is not null;
      update orders set cancelled_at = placed_at + interval '3 hours' where tenant_id = '{tenant}' and status = 'CANCELLED';
      update platform_ledger l set created_at = o.placed_at + interval '3 hours'
        from orders o where l.reference_type = 'ORDER' and l.reference_id = o.id and l.tenant_id = '{tenant}'
         and l.entry_type = 'COMMISSION_VOIDED';
      -- Fulfilment follows age: older lines are delivered, recent ones on the road, the newest still waiting.
      update order_lines l set status = 'DELIVERED', dispatched_at = o.placed_at + interval '6 hours',
             delivered_at = o.placed_at + interval '22 hours', tracking_note = 'Delivered, signed for at the gate'
        from orders o where l.order_id = o.id and o.tenant_id = '{tenant}' and l.status = 'ROUTED' and o.placed_at < now() - interval '7 days';
      update order_lines l set status = 'DISPATCHED', dispatched_at = o.placed_at + interval '5 hours',
             tracking_note = (array['Chilled van KBX 421T','Motorbike courier, cool box','Supplier truck, 2 drops before you'])[1 + floor(random() * 3)::int]
        from orders o where l.order_id = o.id and o.tenant_id = '{tenant}' and l.status = 'ROUTED'
         and o.placed_at between now() - interval '7 days' and now() - interval '2 days';
      update orders set status = 'PAID' where tenant_id = '{tenant}' and status = 'ROUTED' and placed_at < now() - interval '5 days';
      insert into mpesa_payments (tenant_id, purpose, reference_id, msisdn, amount_cents, due_cents, merchant_request_id, checkout_request_id,
                                  mpesa_receipt_number, status, result_desc, initiated_at, completed_at)
        select tenant_id, 'ORDER', id, '254712345678', revenue_cents, revenue_cents, 'demo-' || substr(md5(id::text), 1, 10),
               'demo-co-' || substr(md5(id::text), 1, 10), 'DEMO' || upper(substr(md5(id::text), 1, 8)), 'SUCCESS',
               'The service request is processed successfully.', placed_at + interval '9 minutes', placed_at + interval '10 minutes'
          from orders where tenant_id = '{tenant}' and status = 'PAID';
      with w as (select id, row_number() over (order by random()) as rn, count(*) over () as total
                   from wastage_records where tenant_id = '{tenant}')
      update wastage_records x set recorded_at = now() - (w.rn::float / (w.total + 1)) * interval '{WEEKS * 7} days'
        from w where x.id = w.id;
    """)

    now = datetime.now(timezone.utc)
    cut = now - timedelta(days=28)
    first, _ = call("/v1/billing/invoices/generate", {"periodStart": iso(now - timedelta(days=WEEKS * 7 + 1)), "periodEnd": iso(cut)}, token)
    # Upgrading after the first invoice means the second one carries a subscription fee as well.
    call("/v1/billing/subscription/plan", {"plan": upgrade_to}, token, method="PUT")
    second, _ = call("/v1/billing/invoices/generate", {"periodStart": iso(cut), "periodEnd": iso(now)}, token)

    paid = False
    if first.get("id"):
        pay, status = call(f"/v1/billing/invoices/{first['id']}/pay", {"msisdn": "254712345678"}, token)
        if status == 202:
            call("/v1/public/mpesa/stk-callback", {"Body": {"stkCallback": {
                "MerchantRequestID": "demo", "CheckoutRequestID": pay["checkoutRequestId"], "ResultCode": 0,
                "ResultDesc": "ok", "CallbackMetadata": {"Item": [{"Name": "MpesaReceiptNumber", "Value": "DEMO" + first["reference"][-6:]}]}}}},
                method="POST")
            paid = True
    # Make the paid invoice look like it was paid last week rather than just now.
    if paid:
        sql(f"update invoices set issued_at = now() - interval '27 days', paid_at = now() - interval '20 days', due_at = now() - interval '13 days' where id = '{first['id']}'")
    if second.get("id"):
        sql(f"update invoices set issued_at = now() - interval '1 day', due_at = now() + interval '13 days' where id = '{second['id']}'")
    print(f"  {label}: {len(orders) - cancelled} live orders over {WEEKS} weeks, {cancelled} cancelled, {wasted} wastage records, "
          f"invoices {first.get('reference')} ({'paid' if paid else 'open'}) and {second.get('reference')} (open), plan {upgrade_to}")


def demo_logins():
    owner, _ = call("/v1/auth/login", {"email": "grace@mazingira.co.ke", "password": PW})
    token = owner.get("accessToken")
    if not token:
        return
    suppliers, _ = call("/v1/suppliers", token=token)
    customers, _ = call("/v1/customers", token=token)
    limuru = next((s for s in suppliers if s["name"].startswith("Limuru")), suppliers[0])
    shop = next((c for c in customers if c["name"].startswith("Zucchini")), customers[0])
    for email, name, role, extra in [
        ("supplier@mazingira.co.ke", "Limuru Dairy (supplier login)", "SUPPLIER", {"supplierId": limuru["id"]}),
        ("customer@mazingira.co.ke", "Zucchini Greengrocers (shop login)", "CUSTOMER", {"customerId": shop["id"]}),
    ]:
        call("/v1/users", {"fullName": name, "email": email, "password": PW, "role": role, **extra}, token)
    print("  demo logins: supplier@mazingira.co.ke and customer@mazingira.co.ke")


if __name__ == "__main__":
    print("Adding history")
    history_for("grace@mazingira.co.ke", "Mazingira", "GROWTH")
    history_for("daniel@riftfresh.co.ke", "Rift Fresh", "SCALE")
    history_for("amina@coastdairy.co.ke", "Coast Dairy", "FREE")
    demo_logins()
