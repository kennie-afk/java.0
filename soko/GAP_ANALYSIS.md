# FreshFerm (soko) — gap analysis

What exists is genuinely solid: three-sided marketplace (distributor/supplier/customer),
tenant-isolated, cold-chain-and-shelf-life-aware routing that actually refuses bad
matches instead of just picking cheapest, stock that provably cannot be oversold,
measured (not claimed) performance, and a working console for all three roles. This is
demo-ready tonight. What follows is what stands between that and a platform a real
fermented-milk distributor could run her whole business on.

## Closed 2026-09-29

- **M-Pesa STK push on order payment** — done. `com.soko.mpesa` (mock gateway by
  default, real Daraja gateway behind `soko.mpesa.mode=live`), `com.soko.payment` for
  the initiate/callback pipeline, `mpesa_payments` table. Idempotent both ways: a
  second "pay" tap before the PIN prompt is answered returns the same push instead of
  queuing a new one, and a replayed Safaricom callback (`CheckoutRequestID` is the key)
  is a no-op once the payment has already settled. Proved live in
  `tools/monetization_check.py`, not just unit-tested.
- **Order edit/cancel** — `POST /v1/orders/{id}/cancel` on a ROUTED, unpaid order
  restocks every reserved offer and voids the platform commission accrued on it; a PAID
  order is refused (needs a real refund flow, out of scope here). `PATCH
  /v1/products/{id}` and `PATCH /v1/suppliers/{id}` close the rest of "no edit" —
  deactivating a supplier now actually removes them from routing (`RoutingEngine`
  already filtered on `status`, it just had no way to be set to anything else).
- **Product photos** — `products.photo_url`, settable via the new PATCH, returned by
  both the authenticated and public storefront.
- **Wastage/spoilage tracking** — `wastage_records`, `POST /v1/wastage` (also draws the
  quantity down from the offer, using the same oversell-safe conditional update as an
  order), `GET /v1/wastage` for the running total, folded into `/v1/overview`.
- **Platform monetization** (a distinct ask from any of the above — what Soko-the-
  platform earns, not what a distributor earns from their own customers): a `Plan`
  catalogue (FREE/GROWTH/SCALE — placeholder pricing, needs a real number from the
  business owner before this means anything commercially), a `Subscription` per tenant,
  a `PlatformCommission` accrued and snapshotted on every routed order, monthly
  `Invoice` generation (both on demand and via a scheduled job), and an append-only
  `platform_ledger` whose entries for an invoice sum to exactly that invoice's total —
  proved, not asserted, in `tools/monetization_check.py`. See `com.soko.billing`.
- **SMS notifications** — `OrderNotifications` now sends via the existing `SmsSender`
  interface alongside email, on order placed/dispatched/delivered. Still `LoggingSmsSender`
  under the hood (no Africa's Talking account configured) — the code path that was
  entirely missing before now exists and is provably wired, per `SmsSender`'s own
  javadoc: swapping in a real provider later is a one-class change.

Two real bugs surfaced only by running the above against real Postgres, not by the
unit tests (whose hand-rolled fakes don't model Hibernate's flush ordering): a plan
change inserted the new subscription row before flushing the old one's `ended_at`,
and invoice generation ran a bulk `@Modifying` update referencing an invoice id before
that invoice had been flushed to the database. Both are fixed with an explicit
`saveAndFlush` at the right point — see `SubscriptionService`/`InvoiceService`.

**Not done, and deliberately out of scope for this pass:** a real Daraja sandbox
account (mock mode is what's wired and tested), a real SMS provider account, and any
change to CORS/rate limiting/observability/backups (still ranked below, see bottom).

## Already known and documented (from the project's own README)

- **No email.** `POST /v1/auth/forgot` is correctly non-leaking (answers identically
  whether the account exists) but sends nothing. No order confirmations, no receipts by
  email either.
- **No delivery routing or proof of delivery** beyond a supplier manually marking a line
  dispatched then delivered. No ETA, no driver assignment, no geolocation, no photo/
  signature proof.

## Found by reading the code

**Wide-open CORS.** `SecurityConfig` allows any origin (`addAllowedOriginPattern("*")`)
with all methods. Fine for a demo behind a bearer token, but for production this should
be scoped to the actual console/storefront domains once those are fixed.

**No rate limiting anywhere.** The auth endpoints (`/auth/login`, `/auth/register`,
`/auth/forgot`) have no throttling. `/auth/forgot`'s careful non-leaking design is
undermined if an attacker can hammer it to enumerate registered emails via timing, or
just brute-force login, without any rate limit slowing them down.

**No search or filtering on the storefront.** `GET /v1/shop/products` returns the whole
catalogue; fine for nine SKUs, will not stay fine once the line grows (new flavours,
sizes, seasonal products).

**No low-stock alerting.** An offer's `availableQty` only decreases (on order) or gets
manually reset (supplier restocks via `PUT`). Nothing tells a supplier or the
distributor "you're about to run out" — they find out when an order gets refused for
insufficient quantity, which is the worst time to find out.

**No structured observability.** No metrics, tracing, or centralized logging beyond
whatever Spring Boot Actuator's `/health` gives by default (health checks are wired in
the Dockerfile). Fine for a single demo tenant; would need attention before running
paying customers' businesses on it.

**No backup/disaster-recovery story.** Single Postgres container with a named volume.
No documented backup schedule or restore drill. Not a code gap, an operations gap, but
a real one before trusting real order/payment data to it.

## Not gaps — deliberately, correctly left out for now

- Multi-currency: unnecessary, this is a KES-only Kenyan business.
- Vision/ANPR, chemical dosing, machine interfaces (README's own "not built yet" list):
  correctly deferred, not needed for a dairy dropshipping platform at all.
- Microservices split: correctly avoided — see the earlier architecture discussion, one
  service is the right size for this business.

## If I had to rank what to build next for a real (not just demo) launch

Everything from the previous ranked list (M-Pesa, order edit/cancel, product photos,
wastage, SMS) is done — see "Closed 2026-09-29" above. What's left, ranked:

1. A real Daraja sandbox account and a real SMS provider account — both are Kennedy's
   to supply, not a code gap; the integration points are built and tested against mocks.
2. Low-stock alerting — an offer only ever finds out it's empty when an order refuses
   it. Worth building now that wastage recording proves the "surface the number nobody
   currently sees" pattern works.
3. Search/filtering on the storefront, once the catalogue is bigger than nine SKUs.
4. Rate limiting, CORS scoping, structured observability, backup/DR story — real, but
   matter more at scale than for the first paying client.
