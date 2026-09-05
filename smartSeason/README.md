# SmartSeason

An agricultural operations platform for Kenya: farms and seasons, IoT-driven automation,
workforce management with fraud detection, a produce marketplace, logistics, and
M-Pesa-backed payments with a double-entry ledger.

Built as 27 Spring Boot microservices behind a Spring Cloud Gateway, with a Next.js
frontend. Every service owns its database and communicates asynchronously over Kafka.

## Stack

| Layer | Choice |
|---|---|
| Language | Java 21 |
| Services | Spring Boot 3.5.16, Spring Data JPA, Spring Security |
| Edge | Spring Cloud Gateway 2025.0.3, Resilience4j circuit breakers, Redis rate limiting |
| Database | PostgreSQL 16, Flyway forward-only migrations |
| Messaging | Kafka (Redpanda in local development) |
| Frontend | Next.js 16, React 19, TypeScript, Tailwind CSS 4 |
| Observability | Micrometer, Prometheus, Grafana, Spring Boot Actuator |
| Docs | OpenAPI 3 via springdoc, served per service at `/swagger-ui.html` |
| Build | Maven, Docker multi-stage images, JUnit 5 + Mockito + AssertJ |

## Quick start

```bash
cp .env.example .env          # then set POSTGRES_PASSWORD and JWT_SECRET
docker compose up -d          # infrastructure, gateway and web only
docker compose --profile all up -d      # everything
docker compose --profile core up -d     # identity, farm, season, agronomy, weather
```

Compose fails fast if `JWT_SECRET` or `POSTGRES_PASSWORD` is unset, rather than starting
with a default secret.

| Endpoint | URL |
|---|---|
| Web | http://localhost:3000 |
| Gateway | http://localhost:8080 |
| Service API docs | http://localhost:{port}/swagger-ui.html |
| Prometheus | http://localhost:9090 (`--profile observability`) |
| Grafana | http://localhost:3001 (`--profile observability`) |

Profiles: `core`, `workforce`, `iot`, `market`, `money`, `platform`, `observability`, `all`.
Running all 27 services locally needs roughly 12 GB of RAM; the profiles exist so a subset
can be run instead.

## Services

### Identity & access

| Service | Port | Database | Responsibility |
|---|---|---|---|
| `identity-service` | 8081 | `identity_db` | Users, organisations, roles, sessions, KYC, JWT issuance + JWKS |

### Farm & agronomy

| Service | Port | Database | Responsibility |
|---|---|---|---|
| `farm-service` | 8082 | `farm_db` | Farms, plots, geo boundaries, soil profiles, cooperative membership |
| `season-service` | 8083 | `season_db` | Crop cycles per plot, stage calendars, planting plans, yields |
| `agronomy-service` | 8084 | `agronomy_db` | Advisories, pest/disease library, scouting reports, crop playbooks |
| `weather-service` | 8085 | `weather_db` | Weather feeds, forecasts, NDVI, agro-climatic alerts per geo cell |

### Automation & IoT

| Service | Port | Database | Responsibility |
|---|---|---|---|
| `device-registry-service` | 8086 | `device_db` | Device identity, provisioning, plot mapping, firmware, credentials |
| `telemetry-ingest-service` | 8087 | `telemetry_db` | Raw + downsampled device telemetry, anomaly emission (highest write volume) |
| `automation-service` | 8088 | `automation_db` | Automation rules, actuator command pipeline, safety interlocks, digital twin |

### Workforce & integrity

| Service | Port | Database | Responsibility |
|---|---|---|---|
| `workforce-service` | 8089 | `workforce_db` | Workers, contracts, wage rates, gangs, supervisors, farm assignment |
| `attendance-service` | 8090 | `attendance_db` | Geofenced biometric clock-in/out, shifts, piece-rate tallies, offline sync |
| `task-service` | 8091 | `task_db` | Work orders, assignments, checklists, photo/GPS evidence, verification |
| `fraud-service` | 8092 | `fraud_db` | Fraud rules engine, anomaly scoring, cases, evidence bundles, review queue |

### Marketplace & commerce

| Service | Port | Database | Responsibility |
|---|---|---|---|
| `catalog-service` | 8093 | `catalog_db` | Produce taxonomy, products, variants, grading standards, certifications |
| `marketplace-service` | 8094 | `marketplace_db` | Supply listings, demand posts, offers, buyer-seller matching |
| `pricing-service` | 8095 | `pricing_db` | Reference prices, market index per commodity/region, price series, suggestions |
| `order-service` | 8096 | `order_db` | Carts, orders, fulfillment saga, returns, disputes |
| `inventory-service` | 8097 | `inventory_db` | Aggregation-centre stock, batches, grading, reservations, farm-input reconciliation |
| `logistics-service` | 8098 | `logistics_db` | Transport jobs, driver/vehicle assignment, routing, cold chain, proof of delivery |

### Money

| Service | Port | Database | Responsibility |
|---|---|---|---|
| `payment-service` | 8099 | `payment_db` | Payment intents, M-Pesa STK/C2B/B2C, cards, wallets, escrow, callback reconciliation |
| `ledger-service` | 8100 | `ledger_db` | Double-entry accounts, immutable postings, balances, statements |
| `payout-service` | 8101 | `payout_db` | Farmer settlements, bulk wage disbursement, fees, scheduling, fraud holds |

### Platform

| Service | Port | Database | Responsibility |
|---|---|---|---|
| `traceability-service` | 8102 | `traceability_db` | Farm-to-fork lineage graph, certifications, QR pass |
| `notification-service` | 8103 | `notification_db` | SMS, USSD, push, WhatsApp, email; EN/SW templating, delivery tracking, quiet hours |
| `media-service` | 8104 | `media_db` | Pre-signed uploads, image/video processing, thumbnails, EXIF/GPS extraction |
| `search-service` | 8105 | `search_db` | Search indexes kept fresh from domain events (listings, farms, workers, produce) |
| `analytics-service` | 8106 | `analytics_db` | Event sink, data marts, dashboards API, scheduled reports, exports |
| `audit-service` | 8107 | `audit_db` | Tamper-evident hash-chained audit log of privileged actions across all services |

27 services, 113 persisted aggregates, one gateway, one web application.

## Design decisions

**Database per service.** No service reads another's tables. State crosses a boundary as a
domain event or not at all. Locally the 27 databases live in one PostgreSQL container for
practicality; the schemas are already separate, so splitting them across instances is a
deployment change rather than a data-model change.

**Tenant isolation enforced by the type system.** Every row carries `tenant_id`, and
repositories expose only tenant-qualified finders — there is no bare `findById` for callers
to reach for. A row belonging to another tenant is indistinguishable from one that does not
exist. An unbound tenant throws instead of widening the query, so the failure mode is a
closed door rather than a silent cross-tenant read.

**Errors as RFC 7807.** Every failure returns `application/problem+json` with a stable
machine-readable `code`. Stack traces and driver messages are logged, never returned.

**Optimistic locking everywhere.** Concurrent writes surface as `409 stale-write` rather
than silently overwriting.

**Forward-only migrations.** Flyway runs at startup with `ddl-auto: validate`, so drift
between the JPA mapping and the schema breaks the build rather than corrupting data.

**Stateless JWT with a gateway pre-check.** The gateway verifies the token and rejects
unauthenticated traffic at the edge; each service verifies independently as well, so a
service is never dependent on the edge for its own security.

**Events keyed by aggregate.** Kafka messages are keyed by aggregate id so that all events
for one aggregate stay ordered on a partition. Publication is toggleable, so a service runs
standalone in tests without a broker.

## Verifying it works

```bash
tools/build-all.sh                              # build and test every service in Docker
GATEWAY=http://localhost:8080 tools/smoke-test.sh   # end-to-end against a running stack
GATEWAY=http://localhost:8080 tools/seed.sh         # demo organisation, farms, workers, listings
cd apps/web && npm run build                    # typecheck and build the frontend
```

`smoke-test.sh` exercises the real request path through the gateway: registration, login,
token rejection, validation, and — most importantly — that one tenant cannot see or fetch
another tenant's data. It asserts `404` rather than `403` for a cross-tenant read, because
disclosing that a resource exists is itself a leak.

Each service ships unit tests covering the tenant-scoping contract: that a create stamps the
caller's tenant and emits an event, that another tenant's row reads as not found, and that a
missing tenant fails closed instead of querying across tenants. Every service also has a
`@SpringBootTest` context-load test, so a service that compiles but cannot start fails the
build rather than crash-looping in a container.

## Layout

```
apps/
  api-gateway/      Spring Cloud Gateway, JWT pre-check, rate limiting, circuit breakers
  web/              Next.js frontend
services/           27 Spring Boot services, one directory each
infra/
  docker/           PostgreSQL bootstrap
  observability/    Prometheus scrape configuration
tools/              service catalogue and the generator that emits scaffolding from it
docs/               architecture notes
```

The service catalogue in `tools/catalogue.py` is the single source of truth for service
names, ports, databases and aggregates. Scaffolding — entities, repositories, DTOs,
migrations, gateway routes, compose entries and Prometheus targets — is generated from it,
so those cannot drift apart. Domain logic is written by hand on top.

