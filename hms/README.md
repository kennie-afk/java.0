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
- Imaging: procedure catalogue, orders, performed study, reports signed by a second person, critical findings, amendments, billing of performed studies. PNG and JPEG images can be attached to a performed study and looked at in a plain viewer (zoom, invert, brightness, contrast); files are served only through short-lived signed links. DICOM is not supported.
- Maternal and child health: antenatal visits with computed warning flags, delivery outcome, postnatal care for mother and baby (computed warning flags, six-week window), family planning (current method, revisits, switches, due list), immunisation schedule, programme summary.
- Programmes: HIV, TB, hypertension, diabetes, asthma and epilepsy registers with follow-up visits, outcomes and defaulter tracing. HIV care has its own permission and every opening of it is audited.
- Billing: price list, invoices frozen on issue, payments, receipts, M-Pesa (simulated).
- Claims readiness (Madai): assembly and readiness checks. The adapter is an unverified stub that sends nothing.
- Inpatient: wards, beds, admissions, transfers, discharge.
- Reporting: operational aggregates, plus configurable report definitions built from a fixed catalogue of measures (no free-form SQL), CSV export and a DHIS2 data value set export (whole calendar months only). Not the official MOH returns.
- FHIR R4 read interface (`/fhir/r4`): Patient, Encounter, Observation, MedicationRequest, AllergyIntolerance. See `docs/FHIR.md`.
- Patient portal: accounts created from a one-time code handed over in person plus the date of birth, a separate token type, results and reports shown only after a clinician releases them, appointment requests, medicines and allergies. Staff are told whether the patient was notified; the invitation code itself is never sent.
- Notifications: e-mail and SMS go through an outbox that a dispatcher drains (leased rows, retry with back-off, safe on several replicas). The providers are a mock that logs a masked line and sends nothing, or `live`, which is not implemented and fails loudly.
- Idempotent writes: any write sent with an `Idempotency-Key` is carried out once.

The Next.js console in `apps/console` covers all of the above, including the patient portal under `/portal`, and keeps working through short connection losses for bedside capture (vitals, notes, visits, doses): see Offline in `docs/ARCHITECTURE.md`.

Deployment: Docker Compose for a single machine, Kubernetes manifests in `k8s/` (see `docs/DEPLOY.md`), and a CI workflow in `.github/workflows/hms.yml` at the repository root.

## Not built or not verified

- No live SHA/DHA submission and no live M-Pesa: both are behind adapters that do not talk to anyone.
- ICD-11 codes are checked for shape only; there is no licensed catalogue. LOINC and PPB codes are stored, not validated.
- Official MOH forms and a KHIS export are not built. The DHIS2 export follows the public data value set format but has not been sent to a DHIS2 server.
- The FHIR interface passes the HL7 validator (R4 4.0.1) with no errors on the seeded data (`scripts/validate_fhir.py`), with terminology checks off because no terminology server was available, so LOINC and UCUM codes were not looked up. It is not claimed to conform to any national implementation guide.
- No real SMS or e-mail provider is connected (mock only). Offline support has been tried in Chrome only (not Safari or Firefox).
- Imaging attachments are PNG and JPEG on local disk. There is no DICOM, no PACS and no S3-compatible store; a deployment with several API pods has no shared storage, so uploads are switched off in `k8s/01-config.yaml`.

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
