# Operating Mara

What a person deploying or running Mara needs, and what has and has not been tried. Sections marked **Tested** say what
was run; anything not marked is a design statement, not a result.

## 1. Credentials (replaces the two global tokens)

`MARA_ADMIN_TOKEN` and `MARA_INTERNAL_TOKEN` are gone: one leak of either was every tenant. Back-office and
service-to-service calls now use **credentials** of the form `mop_<16 hex>.<43 characters>` (256 bits of secret; only a
SHA-256 is stored). Each has scopes, an expiry, an optional tenant binding, and can be revoked or rotated alone.

| Scope | Lets the holder | Who may hold it |
|---|---|---|
| `admin:read` / `admin:write` | read / change the back office of its tenant | operator credentials |
| `platform:tenants` | create a tenant | platform credentials only (no tenant binding) |
| `credentials:manage` | issue, rotate, revoke, list credentials, read their audit | platform credentials only |
| `terminals:lookup`, `credentials:verify`, `sync:feed` | service-to-service calls | service credentials only |

A **tenant-bound** credential is forced onto its tenant: the `X-Mara-Tenant` a caller sends is overwritten, and a different
one is refused (403, audited). Every endpoint names the one scope it needs; a path with no assigned scope is closed.

**First deployment.** Mint three values and put them in the environment of the places that need them:

    python3 scripts/new-credential.py --env      # prints MARA_SVC_SYNC_CREDENTIAL, MARA_SVC_CORE_CREDENTIAL, MARA_BOOTSTRAP_CREDENTIAL

* `MARA_SVC_SYNC_CREDENTIAL` / `MARA_SVC_CORE_CREDENTIAL`: one per service, never shared. identity-service registers their
  hashes at start-up; sync-service and core-service each hold only their own (as `MARA_SERVICE_CREDENTIAL`).
* `MARA_BOOTSTRAP_CREDENTIAL`: optional, lasts 24 h from identity's start, can ONLY manage credentials. Use it to mint the first
  platform operator (`scripts/demo_seed.py` does, for demos), then remove it and restart identity-service: it is then revoked.

**Giving a shop owner access to the back office** (as a platform operator):

    curl -X POST $IDENTITY/v1/admin/credentials -H "Authorization: Bearer $OPERATOR" -H 'Content-Type: application/json' \
      -d '{"label":"Grace Bakery owner","tenantId":"<tenant id>","scopes":["admin:read","admin:write"],"expiresInHours":720}'

The response is the only time the credential is shown. Rotate with `POST /v1/admin/credentials/rotate {"id": "...", "graceMinutes": 30}`
(a replacement with the same label, tenant, scopes and lifetime; the old one runs on for the grace); revoke with
`POST /v1/admin/credentials/revoke {"id": "..."}`; read what happened with `GET /v1/admin/credentials/audit`.

**Rotating a service credential:** put the new value in `MARA_SVC_<SVC>_CREDENTIAL` and the old one in `..._PREVIOUS`, roll every
service, then remove `..._PREVIOUS` and restart identity-service: every seeded credential for that service that is in neither is revoked.

**How sync-service and core-service decide:** they ask identity-service (`/v1/internal/credentials/verify`) with their own service
credential. Only a *success* is cached, for 15 s, so a revoked credential stops working within that window; a refusal is never cached, so a
credential issued a second ago works at once. If identity-service cannot be reached the answer is 503: **fail closed**.

**Tested** (`CredentialLifecycleTest`, `OperatorAuthFilterTest`, `HttpCredentialVerifierTest`, 8 + 5 + 4 tests): tenant binding, scope per
endpoint, issue rules, expiry, revocation, rotation with grace, the audit trail being append-only, the application database role having
no privilege on the credential tables, the verify endpoint, seeding and rotation-by-omission, fail-closed behaviour.
**Not done:** a user login for the back office (a shop owner pastes a credential an operator issued; there is no password or
self-service recovery); credential use is audited for writes and refusals only, not every read.

## 2. Rate limits behind the ingress

Limits are per client address and, with `MARA_REDIS_URL`, **shared across replicas**. If Redis is unreachable each replica falls back to
its own in-process limit: it neither opens up (a Redis outage must not become an unthrottled flood) nor refuses the tills (a till must not be
punished for a cache being down). While degraded the ceiling is N times the limit.

In Kubernetes every till reaches the services through the terminal proxy pod, so counting the socket address would put every shop in one
bucket. `MARA_RATELIMIT_TRUST_FORWARDED_FOR=true` counts the **last** `X-Forwarded-For` entry instead (the one the ingress appended; a
client can only add entries on the left). That is sound only if nothing but the ingress can reach those pods: the NetworkPolicy
guarantees it, and `scripts/check-k8s.py` fails if it stops being true. It is off by default (compose).

## 3. Partitioning the ledger (opt-in, measured)

Not a migration, on purpose: see `docs/CAPACITY-RESULTS.md` §1. At 7.5M rows it cost 15-35% of write throughput and bought no read
speed. Apply it when a ledger is heading towards hundreds of millions of rows, with the service stopped:

    psql "$OWNER_URL" -v ON_ERROR_STOP=1 -1 -f services/core-service/src/main/resources/db/optional/partition-ledger-and-sales.sql
    psql "$OWNER_URL" -v ON_ERROR_STOP=1 -1 -f services/sync-service/src/main/resources/db/optional/partition-journal.sql

`-1` makes it one transaction: a failure leaves the old tables untouched. It refuses clearly if the tables are already partitioned.
Migrating 7.5M rows took 3.5 minutes. **Tested** as a non-superuser owner on existing data (`PartitionMigrationTest`, core and sync).

## 4. Connections

Each replica of each service holds up to `MARA_DB_POOL_SIZE` (default 10 in k8s) application connections, plus a small owner pool.
Postgres sees `replicas x pool x 3 services`: 20 replicas of each at the default is 600, above the 300 the bundled Postgres allows. Either
lower the pool, raise `max_connections` (memory per connection), or put PgBouncer in transaction mode in front of the **application** role
(never the owner role: Flyway needs a session). **PgBouncer in front of Mara has not been tried**; the tenant is bound per transaction
(`set_config(..., true)`), which should be compatible, but that is a statement about the code, not a result.

## 5. Back office

`apps/office` (port 3200): today's sales, sales by day / till / cashier, tills (add, suspend, revoke), staff (add, suspend, remove),
exceptions, audit trail. Its server side keeps the credential in an AES-256-GCM-sealed `httpOnly` `SameSite=Strict` cookie, calls only
the fixed list of operations in `src/lib/routes.ts` (no credential management, no tenant creation, nothing internal), and refuses
state-changing calls that lack a same-origin marker. Set `OFFICE_SESSION_SECRET` (32+ characters) and, over HTTPS, `OFFICE_COOKIE_SECURE=true`.
