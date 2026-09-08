# Load Tests

Requires [k6](https://k6.io/docs/get-started/installation/).

## Property search & detail load test

Ramps to 200 concurrent virtual users against the cached public search and detail endpoints.

```
k6 run load-tests/property-search-load-test.js
k6 run -e BASE_URL=https://staging.smartre.co.ke load-tests/property-search-load-test.js
```

Pass thresholds: p95 latency under 2s, error rate under 5%.

## Gateway rate limiter verification

Fires 150 requests/second at a single endpoint for 10 seconds to confirm the documented
50 req/s (burst 100) Redis rate limiter actually rejects excess traffic with 429s.

```
k6 run load-tests/rate-limiter-verification.js
k6 run -e TEST_TOKEN=<jwt> load-tests/rate-limiter-verification.js
```

If the summary reports zero 429s under this load, the rate limiter is not enforcing and
needs investigation before relying on the "50 req/s per user" scaling claim.

## Authenticated tenancy load test

The search test measures cached, public reads. This one measures what a landlord and a
tenant actually wait for: portfolio summaries, invoice lists and maintenance history,
none of which are cached and all of which grow without bound.

```
k6 run -e LANDLORD_TOKEN=<jwt> -e TENANT_TOKEN=<jwt> load-tests/tenancy-load-test.js
```

Each scenario skips itself if its token is missing, and the summary says which were
skipped — a run with no tokens otherwise looks like a clean pass.

Thresholds: p95 under 3s overall, under 2.5s for the tenant dashboard, errors under 5%.
A 429 counts as an error here; at these rates the per-service limiter should not be
engaging.

## M-Pesa callback idempotency under concurrent retries

Fires 100 callbacks/second for 30 seconds drawn from a pool of only five
CheckoutRequestIDs, so the same payment confirmation is genuinely in flight several times
at once — the shape of a Safaricom retry storm, not a sequence of distinct payments.

```
k6 run -e CALLBACK_SECRET=<MPESA_CALLBACK_SECRET> load-tests/payment-callback-load-test.js
```

The endpoint returns 200 whether or not the payment was posted, because telling Safaricom
about our internal state only makes it retry. So the assertion is in the database, and
the summary prints the two queries to run:

```sql
SELECT checkout_request_id, count(*) FROM payments
 WHERE checkout_request_id LIKE 'ws_CO_LOADTEST_%' GROUP BY 1 HAVING count(*) > 1;
SELECT payment_id, count(*) FROM payment_outbox_events GROUP BY 1 HAVING count(*) > 1;
```

Both must return zero rows. Run it from a host on the callback IP allow-list, or every
response is a 403 and the run proves nothing — the summary will tell you if that
happened.
