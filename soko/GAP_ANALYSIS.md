# FreshFerm (soko) — gap analysis

What exists is genuinely solid: three-sided marketplace (distributor/supplier/customer),
tenant-isolated, cold-chain-and-shelf-life-aware routing that actually refuses bad
matches instead of just picking cheapest, stock that provably cannot be oversold,
measured (not claimed) performance, and a working console for all three roles. This is
demo-ready tonight. What follows is what stands between that and a platform a real
fermented-milk distributor could run her whole business on.

## Already known and documented (from the project's own README)

- **No payment capture.** Orders record what is owed; no money actually moves. For a
  real launch this is the single biggest gap — Kenya's dairy/beverage retail runs on
  M-Pesa, and a dropshipping platform that can't take payment isn't dropshipping yet,
  it's an order log.
- **No email.** `POST /v1/auth/forgot` is correctly non-leaking (answers identically
  whether the account exists) but sends nothing. No order confirmations, no receipts by
  email either.
- **No delivery routing or proof of delivery** beyond a supplier manually marking a line
  dispatched then delivered. No ETA, no driver assignment, no geolocation, no photo/
  signature proof.

## Found by reading the code

**No update or delete on almost anything.** The API has exactly one `PUT` in the whole
system (`PUT /v1/supplier/offers/{id}`, letting a supplier restock/reprice their own
offer) and zero `DELETE`s. Concretely, as a distributor you currently cannot: cancel or
edit an order once placed, edit a product's price or shelf-life data after creation,
deactivate a supplier who's underperforming or has left, or edit a customer's phone/
county. Everything is create-and-view only. For a real operator this is the gap that
will surface first — mistakes happen, prices change, suppliers churn.

**No product photos.** `Product` has no image field at all. A storefront selling a new
drink to health shops and gyms without a picture of the bottle is a real handicap —
buyers who've never heard of the brand are deciding on trust plus a photo, not just a
SKU and a price.

**No M-Pesa integration despite the domain being exactly right for it.** Every other
Kenyan platform in this portfolio (mara, smartRE, smartSeason) has Daraja/STK-push
wired in; soko doesn't yet, even though "no payment capture" is already the top-listed
gap. This is the natural next build, and there's an in-house Daraja integration pattern
to reuse (the `carwash`/Forecourt project's `mpesa/` module) rather than building it from
scratch.

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

**No wastage/spoilage tracking.** This is specific to the domain, not generic SaaS
advice: a fermented-milk distributor's real operational pain is stock that goes bad
before it sells. The platform tracks shelf life for *routing* purposes but has no way
to record "these 20 units expired unsold" — which is exactly the number a real owner
would want on her dashboard, the same "make the invisible loss visible" idea the
Shahidi/carwash product is built on elsewhere in this portfolio.

**No SMS.** For customers and suppliers who won't be checking a web console
constantly, order-status SMS (via Africa's Talking or similar, both cheap and standard
in Kenya) is a bigger real-world reach multiplier than email would be here.

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

1. M-Pesa STK push on order payment — turns this from an order log into an actual sales
   channel. Highest leverage, and there's already an in-house pattern to copy.
2. Order edit/cancel and product/supplier update endpoints — the gap most likely to
   cause a support headache in week one.
3. Product photos on the storefront.
4. Wastage/spoilage recording, surfaced on the distributor overview — the number a
   real fermented-milk seller actually wants to see and doesn't currently have anywhere
   else to see.
5. SMS notifications for order status.

Everything below that (rate limiting, CORS scoping, search, observability, backups) is
real but matters more at scale than it does for the first paying client.
