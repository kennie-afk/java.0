# HMS

Health management system for Kenya. See `docs/ARCHITECTURE.md` for scope, module map,
phases and what is deliberately not verified (live SHA/DHA submission).

## Status

Phase 0 foundation, built and tested against a real Postgres:

- Tenancy enforced by Postgres row-level security (the API role cannot bypass it; the service refuses to start if it can).
- Staff accounts, sign-in with lockout, per-organisation roles stored as data, permissions resolved per request.
- Hash-chained, append-only audit trail with a verifier.
- Master patient index: Kenyan identifiers, duplicate detection before registration, optimistic concurrency, merge, restricted records that need a recorded reason, keyset-paged search.

## Run the tests

```bash
docker run -d --name hms-pg -e POSTGRES_USER=hms -e POSTGRES_PASSWORD=ownerpw -e POSTGRES_DB=hms_test -p 55436:5432 postgres:16-alpine
cd backend
docker run --rm --network host -v "$PWD":/build -v ~/.m2:/root/.m2 -w /build maven:3.9-eclipse-temurin-21 mvn -B test
```

## Configuration

No insecure defaults: `HMS_JWT_SECRET` (32+ characters), `HMS_DB_OWNER_PASSWORD` and `HMS_DB_APP_PASSWORD` are required.
The owner role runs migrations; the API connects as `HMS_DB_APP_USER`.
