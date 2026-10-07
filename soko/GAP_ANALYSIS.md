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
  catalogue (FREE/GROWTH/SCALE — real pricing set 2026-09-29: 5%/3.50%/2.00% commission
  with KES 0/2,999/9,999 monthly fees, see the rationale in `Plan.java`'s javadoc;
  unvalidated against real tenant willingness to pay, but no longer a placeholder), a
  `Subscription` per tenant,
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

**Not done in that pass:** a real Daraja sandbox account (mock mode is what's wired and
tested) and a real SMS provider account. CORS and rate limiting were closed afterwards; see
"Closed 2026-10-06".

## Closed 2026-10-06

Verified in code first, then fixed with tests (`mvn test`: 99 cases, 0 skipped, against a real
Postgres as the restricted `soko_app` role).

- **Reads returned nothing under row-level security.** A declared repository query method
  (`findBy...`, `@Query`) runs outside any transaction, and the tenant is bound only when a
  transaction begins (`TenantAwareDataSource`), so every list read made straight from a controller
  ran with no tenant and fail-closed to an empty result: a supplier created a moment earlier was
  invisible to `GET /v1/suppliers`, and `POST /v1/offers` answered "no such supplier". Reproduced on
  the previous commit. Every repository interface is now `@Transactional(readOnly = true)` (its
  `@Modifying` and locking methods read-write). The end-to-end test (`ApiEndToEndDatabaseTest`) is
  what exposed it; the earlier tests drove SQL or services directly and never crossed it.
- **Silent truncation.** List endpoints returned the first 50 rows with no way to see the rest.
  Products, suppliers, offers, customers, orders and users now take `page`, `limit` (max 200) and
  `q` (case-insensitive, `%` and `_` matched literally) and answer with `X-Total-Count` and
  `X-Has-More` while the body stays a plain array. The console shows search and pagers on every list
  and uses search-as-you-type pickers on the order, offer and account forms, so the 51st product is
  orderable. The public storefront is read page by page rather than cut at the first.
- **Storefront** filtered out-of-stock rows in Java after fetching 500; the stock filter is now SQL
  (`having sum(available_qty) > 0`), with a matching count, paging and `q`.
- **Customer M-Pesa payment has a UI.** The order page and the post-checkout drawer ask for a
  number (any common Safaricom format, validated server-side as `254[17]XXXXXXXX`), send the prompt
  and poll `GET /v1/shop/orders/{id}/payment` until the phone is answered, showing paid with the
  receipt, the failure reason with a retry, or a stop-polling message after two minutes.
- **Admin screens** for the write endpoints: create and edit suppliers and products, create offers,
  and an owner-only Team screen (list, add, suspend, reactivate) behind a new `GET /v1/users`.
  Operators do not see Team or Billing. Validation messages are given per field.
- **Order placement is idempotent.** `Idempotency-Key` (max 80 characters) on `POST /v1/orders` and
  `/v1/shop/orders`, backed by a unique index on `(tenant_id, key)`. A retry returns the original
  order and reserves nothing; the same key for a different customer or basket is refused. The
  console forms send a key that lives as long as the form or basket.
- **Stock leak on a failed multi-line order.** `reserve()` commits in its own transaction, so a line
  that failed after an earlier line had reserved stock left that stock lost. It is now released.
- **Unmatched payments are visible.** A succeeded M-Pesa payment whose order or invoice is missing
  (or whose order was cancelled meanwhile) was logged and skipped. It is now kept with status
  `ORPHANED` and a reason, written to the ledger as `PAYMENT_ORPHANED`, and listed for the owner at
  `GET /v1/payments/orphaned` and on the Billing screen.
- **Password reset.** `POST /v1/auth/forgot` now e-mails a single-use link (30 minutes, only the
  token's SHA-256 stored, a newer request voids older ones) and `POST /v1/auth/reset` sets the new
  password. The default sender only logs; `SOKO_MAIL_ENABLED=true` plus Spring's `SPRING_MAIL_*`
  settings send over SMTP (`spring-boot-starter-mail`). Production refuses to start without it.
  Sessions already issued stay valid until they expire.
- **Migration freeze check**: `scripts/check-migrations-frozen.py`, run in CI before the build.
- **CORS** is scoped by `SOKO_ALLOWED_ORIGINS` and **rate limiting** covers login, register, forgot
  and reset (per address, in memory per replica): both were open items in the 2026-09 text below and
  are done.
- CI's database-gated tests now run for real: the workflow's Postgres password did not match the
  tests' default, so they would have skipped (and failed the "did not skip" check).

## Still open

- A real Daraja account and a real SMS provider; the code paths are built and exercised against mocks.
  The mock gateway is the only one tried end to end, so no real money has moved.
- Resetting a password does not revoke a JWT already issued; it expires on its own (12 hours by
  default). Revocation would need a token version on the user row.
- The idempotency key is honoured at placement only. A welcome-drawer retry after the one-time code
  was already consumed cannot re-verify, so it surfaces as a code error rather than replaying.
- Orphaned payments are listed, not refunded: refunding is done in the M-Pesa portal.
- The rate limiter is per replica and in memory.

## Already known and documented (from the project's own README)

- **No delivery routing or proof of delivery** beyond a supplier manually marking a line
  dispatched then delivered. No ETA, no driver assignment, no geolocation, no photo/
  signature proof.

## Found by reading the code

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

1. A real Daraja account and a real SMS provider account: both are Kennedy's to supply, not a
   code gap.
2. Low-stock alerting.
3. Structured observability and a backup/restore drill.
4. Token revocation on password reset.
