# HMS

Health management system for Kenya. See `docs/ARCHITECTURE.md` for scope, module map,
phases and what is deliberately not verified (live SHA/DHA submission).

## Status

Backend modules, each with a Flyway migration, row-level security, permissions, audit and tests on a real Postgres:

- Foundation: tenancy, sign-in with lockout, per-organisation roles, hash-chained audit, master patient index.
- Staff, roles and facilities administration (nobody grants more than they hold; the last administrator cannot be removed).
- Scheduling: clinics, sessions, appointments (double booking prevented by the database), priority day queue.
- Clinical: encounters, triage, append-only vitals and notes, ICD-11 diagnoses (code shape only), allergies, orders and prescriptions.
- Pharmacy: formulary, first-expiry-first-out stock, ledger, dispensing, controlled-drug witness.
- Laboratory: catalogue, orders, specimens, four-eyes validation, critical values, amendments.
- Billing: price list, invoices frozen on issue, payments, receipts, M-Pesa (simulated).
- Claims readiness (Madai): assembly and readiness checks. The adapter is an unverified stub that sends nothing.
- Inpatient: wards, beds, admissions, transfers, discharge.
- Reporting: operational aggregates (not the official MOH returns).

A Next.js console in `apps/console` covers all of the above.

## Not built or not verified

- No live SHA/DHA submission and no live M-Pesa: both are behind adapters that do not talk to anyone.
- ICD-11 codes are checked for shape only; there is no licensed catalogue. LOINC and PPB codes are stored, not validated.
- Maternal, programmes, imaging, FHIR facade, patient portal, offline PWA, official MOH forms and KHIS export.

## Run it

```bash
cp .env.example .env   # then replace the three secrets
docker compose up --build
```

Open http://localhost:3600/setup to create an organisation, then sign in.

## Run the tests

```bash
docker run -d --name hms-pg -e POSTGRES_USER=hms -e POSTGRES_PASSWORD=ownerpw -e POSTGRES_DB=hms_test -p 55436:5432 postgres:16-alpine
cd backend
docker run --rm --network host -v "$PWD":/build -v ~/.m2:/root/.m2 -w /build maven:3.9-eclipse-temurin-21 mvn -B test
```

## Configuration

No insecure defaults: `HMS_JWT_SECRET` (32+ characters), `HMS_DB_OWNER_PASSWORD` and `HMS_DB_APP_PASSWORD` are required.
The owner role runs migrations; the API connects as `HMS_DB_APP_USER`.
