# Tenant isolation

A tenant is an organisation. Its id is the organisation's id, assigned by the identity service at
registration and carried in every access token as the `tid` claim. Nothing a client sends can choose
or change it.

Isolation is enforced twice, on purpose.

1. **In the application.** Every generated repository method is tenant-qualified
   (`findByIdAndTenantId`, `findAllByTenantId`, ...), list filters add the tenant condition, caches and
   advisory locks are keyed by tenant, Kafka consumers take the tenant from the event envelope, and
   `tenantId` is never updatable.
2. **In the database.** Postgres row-level security (RLS) on every table that has a `tenant_id`. The
   application role can see and write only the rows of the tenant bound to its current transaction, and
   sees **nothing** when none is bound. A missed filter in application code now returns no rows
   instead of another tenant's.

The second layer exists because the first is code, and code can miss a filter. It is a backstop, not a
replacement: the application filters stay.

## How it works

| Piece | Where | What it does |
|---|---|---|
| Two database roles | `infra/docker/postgres/init-databases.sh`, `k8s/00a-data-layer.yaml` | The **owner** (`POSTGRES_USER`) creates tables and runs Flyway. The **application role** (`APP_DB_USER`, default `smartseason_app`) owns nothing, is not a superuser, has no `BYPASSRLS`, and is what every service connects as. |
| `TenantTransactionManager` | generated, `platform/` | Starts every transaction with `set_config('app.tenant_id', <tid>, true)`. `true` means transaction-local, so it is discarded at commit and is safe behind PgBouncer in transaction-pooling mode, where the next transaction on the same server connection may belong to someone else. |
| `afterMigrate__tenant_isolation.sql` | generated, `db/callbacks/` | A Flyway **callback** run as the owner after every migrate. Finds every table with a `tenant_id`, enables RLS and creates the policy (skipping tables already done, so a restart takes no locks), and grants the application role its privileges. Because it reads the catalogue, a table added by a future migration is protected the next time the service starts. |
| The policy | in that callback | `USING (tenant_id = app_tenant_id()) WITH CHECK (tenant_id = app_tenant_id())`. `app_tenant_id()` is `nullif(current_setting('app.tenant_id', true), '')::uuid`, so unset means NULL and matches nothing. There is deliberately no "or system" clause: an `OR` stops Postgres using the tenant index. |
| `RlsGuard` | generated, `platform/` | Refuses to start if the connected role is a superuser or has `BYPASSRLS`, if it owns the tenant tables, or if any tenant table lacks RLS. A configuration that looks isolated and is not is the failure nobody notices. |
| `TenantSession` | generated, `platform/` | Rebinds the tenant **mid-transaction**, for the few paths that learn their tenant only after a lookup. |
| `ss_*` lookup functions | per-service migrations | The only door through the policy. See below. |

`outbox_events` is excluded: the relay publishes pending events for every tenant, the table is not
exposed through any endpoint, and each event already carries its tenant.

## The paths that start without a tenant

Three kinds of request have no tenant until they have looked something up. Each uses a narrow
`SECURITY DEFINER` function (`ss_*`, run as the owner, which RLS does not apply to) that returns **only a
tenant id**, never a row. The service then binds that tenant and carries on with ordinary, fully filtered
queries.

| Path | Function | Migration |
|---|---|---|
| Sign-in, forgot/reset password, registration's duplicate-e-mail check | `ss_identity_tenant_by_email` | identity `V4` |
| Refresh token | `ss_identity_tenant_by_refresh_hash` | identity `V4` |
| M-Pesa callback (unauthenticated) | `ss_payment_tenant_by_checkout` | payment `V4` |
| Telemetry retention (about every tenant by design) | `ss_telemetry_retention_batch` | telemetry-ingest `V5` |

Registration binds the **new** tenant before inserting, so the rows satisfy `WITH CHECK`. Callbacks that
cannot be attributed are filed under the nil tenant (`00000000-...`), which no real tenant can read.
Kafka listeners already set the tenant from the envelope before calling a transactional service.

## Reference validation

A tenant-scoped finder hides another tenant's rows on a read. Nothing used to stop a **write** from
putting another tenant's id into a reference field, which is an integrity hole and a probe. Generated
services now call `ReferenceChecker.require(entity, field, id)` before they persist, so a foreign id is
refused exactly like a missing one, with the same message.

* **Validated:** every uuid field that points at an entity **owned by the same service** (108 checks across
  create and update). Matched by the naming convention (`farmId` -> `Farm`) plus an explicit alias table
  (`REFERENCE_ALIASES` in `tools/gen_support.py`, e.g. `orderId` -> `PurchaseOrder` in order-service).
* **Not validated:** references to entities owned by **another service** (for example `plotId` on a season,
  `workerId` on a task assignment, `ownerUserId`, `buyerOrgId`). The row lives in another database. The
  referenced service's own tenant-scoped API will answer 404 when anything resolves it, but nothing refuses
  the write at the source. Closing this needs a service-to-service existence check (or events carrying
  the reference's owner), which is a design change, not a generator tweak.
* Hand-written services in `overlay/` (identity team, ledger posting, task my-work, ...) create rows through
  their own code and are not covered by the generated check.

## Object-store keys

`storageKey` on `MediaAsset`, `UploadTicket` and `MediaVariant` is marked `srv` in the catalogue. It is
removed from every request DTO and from the web forms, and the service sets it to
`<tenant id>/<table>/<random uuid>`. A client cannot name its own key, so it cannot point a record at
another tenant's object. No endpoint signs a URL yet; when one is added it must sign only keys that begin
with the caller's tenant id.

## The shared JWT signing key (decision: documented, not changed)

All services and the gateway verify tokens with the same HS256 secret (`JWT_SECRET`), so any process that
holds it can also **mint** a token for any tenant. Row-level security does not help here: a forged token
with a chosen `tid` is a legitimate session for that tenant.

Moving to an asymmetric scheme (identity signs with a private key, every other service verifies with the
public key from a JWKS endpoint) removes the problem, but it touches every service's JWT filter, the
gateway, every secret and every test, and the identity service has no JWKS endpoint today. Per-service
audience claims do **not** fix it (the same key still signs everything). It is therefore recorded as a
known limit, with this mitigation until it is done: the secret exists only as a Kubernetes Secret / compose
env value, is at least 64 bytes, and must be rotated on any suspected exposure (rotation invalidates all
sessions).

## Running it

* **Fresh Postgres:** the init script creates the application role from `APP_DB_USER` / `APP_DB_PASSWORD`.
* **Existing Postgres** (the init script runs only on an empty volume): create the role once and set its
  password. `scripts/demo.sh` does this for the demo stack. For a managed database:

  ```sql
  CREATE ROLE smartseason_app LOGIN PASSWORD '...' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  ```
* **Credentials:** services use `SPRING_DATASOURCE_USERNAME/PASSWORD` (the application role) and
  `SPRING_FLYWAY_USER/PASSWORD` (the owner, migrations only). PgBouncer's user list holds both. In Kubernetes
  the keys are `APP_DB_USERNAME` (ConfigMap) and `APP_DB_PASSWORD` / `DB_PASSWORD` (Secret).
* **Unit tests** run on H2, which has no RLS; they set `smartseason.tenancy.rls=false`. Never set it to
  `false` anywhere else: the guard then only warns.
* **Prove it on a running stack:** `scripts/tenant_isolation_check.py` registers two organisations and checks
  the API behaviour and the database behaviour described above.

## What this does not cover

* The shared JWT key (above), and cross-service references (above).
* An attacker who can run **arbitrary SQL as the application role** can set `app.tenant_id` themselves. RLS
  here stops missed filters in application code; it does not defend against SQL injection, which JPA
  parameter binding is what prevents.
* `outbox_events` is readable by the application role across tenants (see above).
* The owner credentials exist in every service's environment for Flyway. Splitting migrations into a
  separate job with its own credentials would remove that; it is not done.
