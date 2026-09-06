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
| `POST /v1/orders` | 1117 ms | 5199 ms | 7209 ms | 8.9 req/s |

The reads are quick because the overview is one SQL aggregate rather than four table scans
summed in Java, and the order list joins its customer instead of loading every customer per
request. Fixing those two things took the overview from 286 ms to 37 ms.

**Writes are the honest weak point.** Sixteen concurrent workers ordering from thirty-eight
offers collide constantly on the same rows, and Postgres serialises the conditional update
per row, which is exactly what stops overselling. The transaction also spans every line of
an order, so locks are held longer than they need to be. Splitting the reservation out of
the order transaction is the obvious next move; it is not done. A distributor placing a few
thousand orders a day is nowhere near this ceiling, but the number should not be dressed up.

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
```

## Tests

```bash
mvn test
```

Nine cases over the routing engine, covering each refusal in turn: no cold chain for a
chilled product, a lead time that outlasts shelf life, a quantity that cannot be covered,
an inactive supplier, a price tie broken by reliability, and a non-perishable product that
tolerates a slow supplier.

## What is not built

- **No supplier portal.** Suppliers do not sign in; their offers are maintained by the
  distributor. They cannot see orders routed to them or confirm dispatch.
- **No customer storefront.** Buyers do not browse or order themselves; orders arrive
  through the API.
- **No payment capture, delivery tracking or notifications.** `POST /v1/auth/forgot`
  accepts a request and answers identically whether or not the account exists, so it does
  not leak which emails are registered, but no mail is actually sent.

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
