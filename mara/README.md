# Mara

A multi-tenant point-of-sale platform for retail and hospitality, designed around one
constraint: a till must always be able to sell, even mid-partition, without ever putting
the ledger's consistency at risk.

The full reasoning behind every design decision below — why terminals sign their own
journals instead of trusting the network, why fiscal numbers are leased in disjoint
ranges instead of assigned centrally, why row-level security runs even though the
application layer already scopes every query — is written up in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). This file is the practical companion:
what the repository contains, how to build it, and how to run it.

## Why this exists

Point-of-sale software sits on the two sides of CAP at once: the till at the counter must
stay available under a partition, but the tax ledger it feeds must stay consistent. Mara
resolves that by giving the terminal authority over what happened at its own counter —
proven with an on-device Ed25519 signature, a strictly monotonic sequence number, and a
hash-chained tamper-evident journal — and giving the server authority over what everything
means together, enforced not just in application code but at the database itself, with
PostgreSQL row-level security that holds even if a query forgets to scope itself.

## Architecture at a glance

```
mara-platform-parent (aggregator)
├── platform/                  domain logic, no framework, no I/O
│   ├── money/                 currency-safe arithmetic (allocation, rounding)
│   ├── journal/               hash-chained, gap-detecting sale journal
│   ├── fiscal/                disjoint invoice-number leasing
│   ├── identity/              enrolment policy, Ed25519 verification, request signatures
│   ├── staff/                 roles, PIN lockout policy, elevated-action authorisation
│   └── sale/                  the terminal's canonical sale encoding, entry verification, arithmetic checks
└── services/
    ├── service-kit/           two-role database + tenant-bound RLS wiring, terminal request auth, operator tokens
    ├── identity-service/      the trust root: tenants, terminals, enrolment, staff sign-in, RLS
    ├── sync-service/          terminal fan-in: verifies and keeps the append-only second copy of every journal
    └── core-service/          the double-entry ledger, fiscal number leases, sales posted from verified journals
apps/
└── terminal/                  the till: offline-first Next.js PWA (sale, catalogue, local journal, sync, fiscal lease)
```

`platform` is deliberately dependency-free domain logic — every rule above can be (and
is) tested exhaustively without a database, a clock, or a container. `identity-service`
supplies the state that logic needs and makes its decisions durable, and keeps its own
Spring Boot parent so it stays buildable as a standalone Docker context independent of
the aggregator.

## Demo in five minutes

```
cp .env.example .env          # fill the four secrets (openssl rand -hex 24); ports are optional
docker compose up --build -d   # postgres, identity, sync, core and the till
python3 scripts/demo_seed.py  # creates "Mama Njeri Mart", prints staff numbers, PINs and an enrolment code
```

Open the till at `http://localhost:3100` (or `MARA_TERMINAL_PORT`), then:

1. **Enrolment**: enter the printed code and a label. The till generates its Ed25519 key in the browser.
2. **Catalogue > Load demo items**: 30 everyday Kenyan goods (illustrative prices; 0% and 16% VAT rates are
   examples, not tax advice).
3. **Staff**: sign in (`2001` / `4826`, a cashier; `3001` / `5937`, a supervisor). The PINs are development values.
4. **Sale**: new tab, add items, *Take payment*. Cash works as usual; mobile money is a **simulated** M-Pesa
   prompt (no Safaricom call, no money moves; receipt prefixed `MOCK`, a phone ending `00` declines).
5. **Receipt**, **Journal > Verify chain**, **Summary** (daily sales, tender split, top items, per cashier).
6. **Offline**: stop the network (DevTools > Offline) or `docker compose stop identity-service`; selling and PIN
   sign-in (for anyone who has signed in online once) keep working. Re-enrolment is refused by design.

7. **Server copy**: Journal shows what the server has verified. A moment after the first sale the till
   leases a block of fiscal numbers (set `MARA_FISCAL_LEASE_SIZE=10` to watch it renew at 20% and hand the
   old tail back) and later sales are `NUMBERED`. Read the books back with the operator token:
   `GET :8083/v1/admin/ledger/trial-balance`, `GET :8082/v1/admin/chains`, `/v1/admin/exceptions`
   (header `X-Mara-Tenant: <tenant id from demo_seed>`).

What the demo does **not** show, honestly: there is no api-gateway and no back-office UI (the server reads are
operator-token JSON endpoints); stock is not tracked; there is no shared multi-terminal stock, and Summary is per
terminal. Clearing the browser's site data destroys any sale not yet uploaded.

`python3 scripts/demo_seed.py code` mints another enrolment code (15 minutes, single use).
Operator API for tenants, staff and codes: `/v1/admin/*` with `Authorization: Bearer $MARA_ADMIN_TOKEN`.

## Requirements

- Java 21
- Maven (a wrapper is not checked in; use any Maven 3.9+ on your `PATH`, or point
  `MAVEN_HOME` at one)
- Docker, for the identity-service integration tests (they start a real PostgreSQL via
  Testcontainers) and for running the service itself
- PostgreSQL 16, if running outside Docker

## Building

```
mvn clean verify
```

Builds both modules and runs the full test suite, `platform`'s pure unit tests and
`identity-service`'s Testcontainers-backed integration tests included. The integration
tests bring up a disposable `postgres:16-alpine` container, apply every Flyway migration
against it, and tear it down — no shared state between runs, no PostgreSQL install
required on the machine running the build.

To build only the domain logic, without touching Docker:

```
mvn -pl platform -am test
```

## Running identity-service

The service refuses to start without its database credentials — there are no defaulted
fallbacks, because a credential that silently works is worse than a service that visibly
does not start. Copy the example and fill it in:

```
cp services/identity-service/.env.example services/identity-service/.env
```

It expects two distinct database roles against the same schema, not one:

| Role | Used by | Privilege |
|---|---|---|
| `mara_owner` | Flyway, at startup only | owns the tables, runs migrations, may bypass RLS |
| `mara_app` | every request the service handles | cannot alter schema, cannot bypass row-level security |

That split is not incidental. A table's owner bypasses row-level security by default in
PostgreSQL, so if the application connected as the schema owner, every policy in
`V2__row_level_security.sql` would be decorative. `mara_app` is created by the migrations
themselves (`CREATE ROLE mara_app NOLOGIN`) — deliberately with no login, since a
versioned migration should never carry a runtime credential. Nothing further to do by
hand: `AppRoleLoginConfig` grants it a login and the password from
`MARA_DB_APP_PASSWORD` on every startup, ordered to run after Flyway and before the
application's own connection pool is built.

With a PostgreSQL instance reachable at `MARA_DB_URL`:

```
mvn -pl services/identity-service -am spring-boot:run
```

The service listens on `:8081`. Its one endpoint that runs without a tenant context is
`POST /v1/enrolment` — see `EnrolmentController` — and every other route is scoped to the
tenant carried in the request, enforced twice: once by `TenantFilter` in application code,
and again by the database itself.

## Running with Docker

```
cp services/identity-service/.env.example .env   # at the repo root, not inside services/
docker compose up --build
```

`docker-compose.yml` at the repo root brings up Postgres and `identity-service` from a
genuinely empty volume — no manual role, password, or migration step. `identity-service`'s
`Dockerfile` builds `platform` and `identity-service` together in one multi-stage image
(its build `context` is the repo root, since it depends on `platform` by Maven coordinate,
not a reactor-relative path — see the Dockerfile's own first comment), then runs on a
minimal `eclipse-temurin:21-jre-alpine` image as a non-root user. Postgres exposes `5433`
on the host (not `5432`), so it will not collide with another local Postgres; the service
itself is on its usual `8081`.

## Testing philosophy

- **Pure logic first.** `EnrolmentPolicy`, `PinPolicy`, `FiscalLease`, `Money` and the
  journal verifier take no framework and no I/O, so their tests run in milliseconds and
  cover edge cases exhaustively — every lockout doubling, every fiscal number in a lease,
  every gap/duplicate/restart shape a journal can present.
- **Concurrency claims are proven, not asserted.** Where two components are each correct
  in isolation but can race against each other — two enrolments for the same tenant's
  last licence slot, for instance — the test drives real concurrent transactions against
  a real database and asserts the invariant held, rather than reasoning about it from the
  trigger's SQL alone.
- **Database migrations are never edited once shipped.** A defect discovered in
  `V1` or `V2` is closed by a new migration, never a rewritten one — production has
  already applied the old one, and Flyway's checksum validation would refuse a changed
  file anyway.

## The terminal (`apps/terminal`)

A Next.js 16 / React 19 / Tailwind 4 app that is a working offline till on its own, in
one browser. It needs `identity-service` once, to enrol; `sync-service` and `core-service`
are optional for selling and only add the server's copy and fiscal numbers.

- **Enrolment** — posts to the real `POST /v1/enrolment` via `/api/enrol` (a same-origin
  proxy, base URL from `IDENTITY_BASE_URL`). The Ed25519 key is generated in the browser
  (non-extractable) and kept in IndexedDB; a refused attempt keeps nothing.
- **Sale** — a user-managed catalogue (empty until you add items, each add/edit its own
  route), tabs, split-the-bill, cash and mobile-money tender (the mobile-money reference is
  typed by the cashier and not verified), receipt. Money is BigInt minor units.
- **Journal** — hash-chained, signed, strictly monotonic, paged; **Verify** reports
  intact / broken-at / gap-at. There is deliberately no way to edit or delete an entry.
- **Fiscal** — leases a block of fiscal numbers from `core-service`, draws from it offline,
  renews at 20% remaining; a till that has never been online issues `FISCAL_PENDING` and says so.
- **Sync** — a background agent uploads the journal to `sync-service` (signed requests); the
  Journal page shows what the server has verified, what is waiting, and any exception.
- **Status** — enrolled or not, online or offline, whether identity-service is reachable.

```
cd apps/terminal
npm ci
npm test                 # vitest: money, digests, verifier, journal, tamper cases
npm run build && npm start   # http://localhost:3100  (IDENTITY_BASE_URL=http://localhost:8081)
```

It must be served from `localhost` or HTTPS (WebCrypto and service workers require a
secure context) and needs a browser with Ed25519 in WebCrypto (Chrome 137+, Firefox 129+,
Safari 17+). `docker compose up --build` also starts it on `:3100`
(`MARA_TERMINAL_PORT` to change), wired to `identity-service`.

**Compatibility with the Java platform is tested, not claimed.** `apps/terminal/vectors/
Vectors.java` runs the platform's own `Money`, `ChainDigest`, `JournalVerifier` and
`FiscalLease` and writes `test/fixtures/java-vectors.json`; the vitest suite asserts the
TypeScript port produces the same allocations, roundings, digests, verdicts and lease
draws. Regenerate with `apps/terminal/vectors/generate.sh` (needs Docker only).

## Project status

Built and tested: `platform`, `service-kit`, `identity-service`, `sync-service`,
`core-service` and `apps/terminal`, with the cross-language contract between the terminal and
the Java platform tested in both directions. Not built: `api-gateway` (the till does not need
one yet), `apps/platform` (a back-office UI), and in `core-service` the catalog, inventory,
tabs and hospitality modules. [`docs/ARCHITECTURE.md §8`](docs/ARCHITECTURE.md) states what is
built, what each piece proves and what it does not, and the exact test counts.

## Running the tests

```
# platform (pure Java, no database)
mvn -pl platform test

# the three services need a PostgreSQL superuser to run against (one container is enough;
# each service creates its own database on it)
docker run -d --name mara-it-pg -e POSTGRES_USER=mara_owner -e POSTGRES_PASSWORD=owner-secret \
  -e POSTGRES_DB=mara_identity -p 55444:5432 postgres:16-alpine
mvn -pl platform,services/service-kit install -DskipTests
mvn -pl services/identity-service test -Dmara.test.jdbc.url=jdbc:postgresql://localhost:55444/mara_identity
mvn -pl services/sync-service     test -Dmara.test.jdbc.url=jdbc:postgresql://localhost:55444/mara_sync_test
mvn -pl services/core-service     test -Dmara.test.jdbc.url=jdbc:postgresql://localhost:55444/mara_core_test

# the till
cd apps/terminal && npm ci && npm test
```

Without `-Dmara.test.jdbc.url` the database-backed tests are **skipped, not passed**; read the
surefire summary's "Skipped" count.
