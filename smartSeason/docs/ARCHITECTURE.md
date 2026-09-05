# Architecture

## Shape

27 Spring Boot services, one Spring Cloud Gateway, one Next.js application.
Each service owns a PostgreSQL database and publishes domain events to Kafka.
No service reads another service's tables.

```
                      browser
                         │
                    Next.js (3000)
                         │
              Spring Cloud Gateway (8080)
        JWT pre-check · rate limit · circuit breakers
                         │
   ┌─────────────────────┼─────────────────────┐
   │                     │                     │
identity  farm  season  …  fraud  payment  ledger  …     (27)
   │                     │                     │
   └──── PostgreSQL (one database per service) ┘
                         │
                       Kafka
```

## Request path

1. The gateway matches `/api/<service>/**` to exactly one upstream. Prefixes are
   unique per service, derived from the service name, so routes cannot collide.
2. `JwtPreCheckFilter` verifies the token, rejects unauthenticated traffic at the edge,
   and forwards the subject and tenant as headers.
3. Resilience4j wraps every route. A failing upstream returns `503` in
   `application/problem+json` from the gateway's fallback rather than hanging the caller.
4. Redis token buckets rate-limit per authenticated user, falling back to client IP.
5. The service verifies the token again independently. The edge is a filter, not the
   only line of defence — a service reached directly is still closed.

## Tenancy

Tenant is the organisation. `identity-service` mints a tenant id at registration; the
organisation is its own tenant root, so `organisation.id == organisation.tenant_id`.

Every table carries `tenant_id` from the first migration. Repositories expose only
tenant-qualified finders:

```java
Optional<Farm> findByIdAndTenantId(UUID id, UUID tenantId);
Page<Farm> findAllByTenantId(UUID tenantId, Pageable pageable);
```

There is deliberately no bare `findById` for application code to reach for. A query that
forgets the tenant is a cross-tenant data leak, so the type system is used to make that
mistake hard rather than relying on review discipline. `TenantContext.requireTenantId()`
throws when no tenant is bound, so the failure mode is a closed door rather than a query
that silently spans every tenant.

`tenant_id` on every row from day one also keeps tenant-aligned sharding available later
without a data-model migration.

## Persistence

- PostgreSQL 16, Flyway forward-only migrations, `ddl-auto: validate`. Drift between the
  JPA mapping and the schema fails startup instead of corrupting data.
- `@Version` optimistic locking on every aggregate. Concurrent writes surface as
  `409 stale-write`, never as a silent overwrite.
- `open-in-view: false`. Lazy loading outside a transaction fails in tests rather than
  issuing surprise queries during view rendering.
- Reads run in a read-only transaction; only mutating methods open a read-write one.

Locally the 27 databases share one PostgreSQL container for practicality. They are separate
databases with separate schemas and no cross-database queries, so moving each onto its own
instance is a deployment change, not a data-model change.

## Events

Events are published with an envelope carrying `eventId`, `eventType`, `version`,
`tenantId`, `aggregateId`, `occurredAt` and `producer`. Messages are keyed by aggregate id,
so all events for one aggregate land on the same partition and stay ordered.

Topics follow `ss.<domain>.<event>.v1`. The version is in the topic name so a breaking
payload change becomes a new topic rather than a silent break for existing consumers.

Publication is switched by `smartseason.events.enabled`. With it off a service runs fully
standalone — the test suite needs no broker.

## Errors

Every failure is RFC 7807 `application/problem+json` with a stable `code`:

```json
{
  "type": "https://docs.smartseason.io/errors/stale-write",
  "title": "stale-write",
  "status": 409,
  "detail": "The resource was modified by another request; reload and retry",
  "code": "stale-write",
  "instance": "/api/farm/v1/farms/6f1c...",
  "timestamp": "2026-09-04T18:22:41Z"
}
```

Clients branch on `code`, never on prose. Stack traces and driver messages are logged
server-side and never returned; a constraint violation becomes `409 constraint-violation`
rather than leaking a table or index name.

## Security

- Stateless JWT. No server-side session, so CSRF protection is unnecessary and disabled;
  every mutating call must carry an explicit bearer token.
- Signing keys shorter than 64 bytes are rejected at construction rather than at first use.
- BCrypt at cost 12 for passwords.
- Login failures are indistinguishable: an unknown account and a wrong password return the
  same message, so the endpoint cannot be used to enumerate users. Accounts lock after five
  consecutive failures.
- Refresh tokens are stored as SHA-256 hashes, single-use, and rotated on every refresh.
  Reuse of an already-revoked token revokes the whole family, which is the standard response
  to a suspected stolen token.
- `TenantContext` is cleared in a `finally` block: the thread returns to the container pool,
  and a leaked tenant would leak into an unrelated request.

## Fraud detection

`fraud-service` carries the platform's most interesting domain logic. A rules engine scores
worker activity against configurable detectors:

| Detector | Signal |
|---|---|
| `GhostWorkerDetector` | an active contract with no attendance and no work evidence |
| `ProxyClockInDetector` | mocked GPS, weak biometric match, or a clock event outside the geofence |
| `ImpossibleTravelDetector` | two clock events implying a speed no journey could achieve |
| `PieceRateInflationDetector` | output far above the peer median, or above the agronomic ceiling for the plot area |
| `HoursInflationDetector` | shift length beyond a plausibility ceiling |

Each detector returns a `Detection` with a confidence in `[0,1]`, a severity derived from
it, a human-readable explanation, and a structured evidence map. Confidences compound
probabilistically rather than summing:

```java
residual *= (1.0 - detection.confidence());
score = round((1.0 - residual) * 100);
```

Two independent signals at 0.5 give 75, not 100. Detections are returned strongest-first.

Every detection carries its evidence and its reasoning, because these decisions affect
someone's pay. Enforcement is a separate decision from detection: scores and cases are
outputs, and a human reviews before any punitive action.

## Code generation

`tools/catalogue.py` is the single source of truth for service names, ports, databases and
aggregates. The generator emits the mechanical layer from it — JPA entities, tenant-scoped
repositories, DTOs, SQL migrations, gateway routes, compose entries, Prometheus scrape
targets. Those artifacts cannot drift out of sync because they have one source.

Hand-written domain logic lives in `overlay/` and is copied over the generated tree, so
regenerating scaffolding never destroys it. Authentication and the fraud engine are written
by hand; the CRUD around them is not, because writing 113 near-identical repositories by
hand would introduce inconsistency without adding judgement.

The generator also enforces invariants: a domain field colliding with a `BaseEntity`
field fails generation with a named error rather than producing Java that does not compile.

## Testing

Every service ships unit tests for the tenant-scoping contract:

- a create stamps the caller's tenant and emits an event
- another tenant's row reads as not found
- a missing tenant fails closed instead of querying across tenants

`identity-service` adds tests for tenant-root creation, password normalisation and hashing,
credential-enumeration resistance, lockout after repeated failures, and rejection of a short
signing key. `fraud-service` adds 16 tests over the rules engine covering each detector's
positive and negative cases, score compounding and bounding, ordering, and the haversine
distance against a known city pair.

```bash
tools/build-all.sh
```
