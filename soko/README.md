# Soko

Dropshipping for dairy and farm produce. A distributor sells milk, yoghurt, eggs and
vegetables without holding any of it: each order line is routed to a supplier who ships
direct. The difficulty is not the marketplace, it is that the goods spoil.

## What makes it dairy rather than general e-commerce

A generic dropshipping router picks the cheapest supplier with stock. That is wrong for
milk. The routing engine in `src/main/java/com/soko/routing/RoutingEngine.java` refuses a
supplier when:

- the product needs a cold chain and the supplier has none;
- the supplier's lead time meets or exceeds the product's shelf life, so the goods would
  arrive already spoiled;
- the supplier cannot cover the quantity, or is not active.

Among the suppliers that survive those rules it takes the cheapest, breaking ties on
reliability and then on lead time. Every rejection carries a reason, so an order that
cannot be filled says why rather than failing silently.

Fresh milk has a 48 hour shelf life, so a supplier 30 hours away is refused even when it
is cheapest. Potatoes have 30 days and no cold chain requirement, so the same supplier is
accepted. That single distinction drives most of the behaviour worth demonstrating.

## Multi-tenant by construction

Every table carries `tenant_id`, and every query is scoped to the tenant on the caller's
token. A second tenant asking for another tenant's order receives **404, not 403** —
telling a stranger that a resource exists is itself a leak. The bundled demo seeds three
independent distributors against one database.

## Stock cannot be oversold

Reserving stock is a single conditional statement:

```sql
update offers set available_qty = available_qty - :quantity
 where id = :id and available_qty >= :quantity
```

If it updates no rows the stock went to somebody else and the line is refused. There is no
read-then-write window and no lock held across the routing decision.

`tools/oversell_check.py` proves it: 120 concurrent orders against 50 units.

```
accepted: 50    rejected: 70    remaining: 0
accepted + remaining = 50
PASS - no overselling
```

An earlier version took a pessimistic write lock over every candidate row. It was correct
but serialised the whole catalogue; replacing it with the conditional update cut median
write latency from 1036 ms to 120 ms.

## Measured, not claimed

From `tools/load.py`, 300 requests at 16 concurrent against a freshly seeded database on a
4-core laptop, API and Postgres both in containers:

| Endpoint | p50 | p95 | p99 | throughput |
| --- | --- | --- | --- | --- |
| `GET /v1/overview` | 37.5 ms | 82.3 ms | 142.2 ms | 367 req/s |
| `GET /v1/orders` | 39.0 ms | 134.4 ms | 202.2 ms | 305 req/s |
| `GET /v1/offers` | 66.2 ms | 121.6 ms | 134.5 ms | 222 req/s |
| `POST /v1/orders` | 275 ms | 557 ms | 708 ms | 53 req/s |

The reads are quick because the overview is one SQL aggregate rather than four table scans
summed in Java, and the order list joins its customer instead of loading every customer per
request. Fixing those two things took the overview from 286 ms to 37 ms.

Writes went through two rounds. A pessimistic lock over every candidate row was correct but
serialised the catalogue, at 1036 ms. Replacing it with the conditional update above brought
that to 1117 ms under load but still held every row lock for the length of the whole order.
Moving the reservation into its own short transaction, so a row lock lives for one statement
rather than the whole checkout, took it to **275 ms and 53 req/s** — six times the throughput,
with all three hundred requests succeeding rather than sixty of them timing out.

## Running it

```bash
cp .env.example .env        # set POSTGRES_PASSWORD and SOKO_JWT_SECRET
docker compose up --build   # api on 8090, console on 3500
python3 tools/seed.py http://localhost:8090
```

The seed creates three distributors with suppliers, a catalogue, offers, customers and
orders, then prints the credentials. Sign in at the console with
`grace@mazingira.co.ke`.

```bash
python3 tools/load.py http://localhost:8090 300 16   # the table above
python3 tools/oversell_check.py                      # the concurrency proof
python3 tools/roles_check.py                         # role isolation and cost leakage
```

## Tests

```bash
mvn test
```

Nine cases over the routing engine, covering each refusal in turn: no cold chain for a
chilled product, a lead time that outlasts shelf life, a quantity that cannot be covered,
an inactive supplier, a price tie broken by reliability, and a non-perishable product that
tolerates a slow supplier.

## Three sides, three interfaces

The distributor is not the only party. Signing in routes each account to its own area.

**The distributor** sees orders, margin, the catalogue, suppliers and every offer, and can
print a receipt per order showing which supplier filled each line.

**The supplier** sees only lines routed to them, what they are owed on the ones not yet
delivered, and their own prices and stock. They mark a line dispatched with a note on how it
is travelling, then delivered on arrival. A second dispatch on the same line is refused.

**The customer** sees a storefront of what is actually in stock, with a basket, and their own
order history with the progress of each line. They never see supplier cost or margin — those
fields are absent from every shop response, not merely hidden in the interface.

Roles are enforced at the edge, not in the pages. A customer token calling an operator
endpoint receives 403, a supplier token calling the storefront receives 403, and a
cross-tenant read receives 404. `tools/roles_check.py` walks all of it, including a check
that no field named cost or margin appears in any customer-facing payload.

## What is not built

- **No payment capture.** Orders record what is owed; no money moves.
- **No email.** `POST /v1/auth/forgot` accepts a request and answers identically whether or
  not the account exists, so it does not leak which addresses are registered, but nothing is
  actually sent.
- **No delivery routing or proof of delivery** beyond the supplier marking a line delivered.

## Layout

```
src/main/java/com/soko/
  domain/       entities
  persistence/  repositories, including the conditional reserve
  routing/      the routing engine and the order service around it
  api/          one REST controller
  security/     JWT issue and verify, tenant context
  platform/     problem-document error handling
apps/console/   Next.js operator console
tools/          seed, load test, oversell probe
```
