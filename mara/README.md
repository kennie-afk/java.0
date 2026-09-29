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
│   ├── identity/               enrolment policy, Ed25519 verification, terminal lifecycle
│   └── staff/                  roles, PIN lockout policy, elevated-action authorisation
└── services/
    └── identity-service/       the trust root: tenants, terminals, enrolment, PostgreSQL RLS
```

`platform` is deliberately dependency-free domain logic — every rule above can be (and
is) tested exhaustively without a database, a clock, or a container. `identity-service`
supplies the state that logic needs and makes its decisions durable, and keeps its own
Spring Boot parent so it stays buildable as a standalone Docker context independent of
the aggregator.

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

## Project status

`identity-service` — tenants, terminals, staff and enrolment — is implemented, migrated,
tested and RLS-hardened. The other three deployables in the target architecture
(`api-gateway`, `core-service` — catalog, sales, payments, ledger, fiscal leases — and
`sync-service`, described in [`docs/ARCHITECTURE.md §3`](docs/ARCHITECTURE.md)) are
designed but not yet built.
