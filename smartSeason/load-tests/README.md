# Load tests

Written 2026-09-11, because until then SmartSeason had **none** — and the honest position on
whether it carries 10M users is that nothing had ever measured it. Every throughput number in
this repository before today came from a laptop running 33 containers in 11Gi, which describes
the laptop.

```bash
k6 run -e BASE_URL=http://localhost:18080 read-path.js
k6 run -e BASE_URL=http://localhost:18080 deep-pagination.js
```

Credentials default to the demo admin in `docs/TEST-ACCOUNTS.md`; override with `-e EMAIL=`
and `-e PASSWORD=`.

## `deep-pagination.js` — does keyset paging actually work?

Walks the same data by offset and by cursor at depths of 1, 5, 20 and 40 pages, in one run,
against one dataset.

**What to look for is the shape, not the numbers.** Offset should climb with depth, because
`OFFSET n` reads and discards n rows. Keyset should stay flat. If keyset climbs too, the
`(tenant_id, created_at DESC, id DESC)` index from `V3__keyset_indexes.sql` is not being used
— check the plan before changing anything else.

Running out of rows before the deepest page is not a failure; it means the dataset is smaller
than the test asks for. Seed more (`tools/seed_demo.py`) to exercise real depth.

## `read-path.js` — where is the ceiling?

Ramps to 250 VUs across six bounded contexts. Deliberately spread rather than pointed at one
service: 27 services share one PgBouncer and one Postgres, and a single-service test would
never surface that contention.

429s and 503s are counted separately from errors. A 429 is the rate limiter working and a 503
is a circuit breaker working; folding them into an error rate makes both look like faults and
hides the faults that are real.

Read the result in this order:

| symptom | what it means |
| --- | --- |
| p95 rises, throughput flat | a queue, usually the connection pool |
| `rate_limited_429` climbing | the limiter is doing its job |
| `circuit_open_503` climbing | a downstream is unhealthy, look there |
| `errors` climbing with VUs | the actual ceiling |

## Where to run this

**Not on the development laptop.** 33 containers in 11Gi with a load average in the single
digits will produce a number that describes the machine. These are written to be run against a
real cluster — which, as of 2026-09-11, has never happened. That remains the single largest gap
between what this platform is claimed to do and what has been shown.

A throwaway kind or k3d cluster is the cheapest honest starting point, and will also surface
what the static manifest checks structurally cannot: ingress, TLS issuance, the storage class,
image pull, and whether the CNI actually enforces the NetworkPolicy that the per-service rate
limiter depends on.
