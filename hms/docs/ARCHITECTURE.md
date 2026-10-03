# HMS: Health Management System for Kenya

A single platform for the whole care pathway of a Kenyan facility or hospital
group: registration, doctors and staff, outpatient and inpatient care, pharmacy, laboratory,
imaging, billing, SHA claims, reporting, and the patient's own view.

## What "professional" means here

1. **Clinical safety first.** Allergies, drug interactions and critical results are never silent.
   Clinical records are append-only: a correction is a new, signed version, never an overwrite.
2. **Standards, not private formats.** Domain model maps to HL7 FHIR R4. Diagnoses use ICD-11 (what
   the DHA eClaims guide requires), labs use LOINC, medicines use PPB generic codes, facilities use the
   Kenya Master Health Facility List codes, people use the SHA/UPI identifiers.
3. **Provable audit.** Every read and write of patient data is logged, hash-chained and verifiable
   after the fact. "Break-glass" access to a restricted record needs a reason and is flagged.
4. **Privacy by construction.** Data Protection Act 2019 and Digital Health Act 2023: consent
   records, purpose-limited access, a server in Kenya, retention rules, subject-access export.
5. **Scale and isolation.** Tenant isolation is enforced by Postgres row-level security, below
   the application. Big tables are partitioned. The API is stateless and scales horizontally.
6. **Honest about what is unverified.** Nothing here claims a live SHA/DHA connection. Those are
   adapters against the public DHA eClaims FHIR guide, marked unverified until credentials exist.

## Tenancy and identity

`Organisation` (a hospital group or a single clinic) → `Facility` (a site, with its KMHFL code and
KEPH level). Every row carries `org_id`; clinical rows also carry `facility_id`. A request runs in one
transaction with `app.org_id` set, and RLS policies on every tenant table filter on it. The runtime
database role is not the table owner and cannot bypass RLS; the API refuses to start otherwise.

Staff (`practitioner` accounts) hold **roles that are data per organisation**, drawn from a
permission vocabulary in code (`patients:read`, `clinical:write`, `pharmacy:dispense`, `billing:post`,
`claims:submit`, ...). A person is assigned to one or more facilities and sees only those.

## Modules (a modular monolith: one deployable, strict package boundaries)

| Module | Responsibility |
| --- | --- |
| `platform` | tenancy, authentication, RBAC, audit chain, consent, errors, paging, idempotency |
| `registry` | master patient index: identifiers, demographics, next of kin, duplicate detection, merge |
| `staff` | practitioners, licences (KMPDC/Nursing Council/PPB), departments, rosters |
| `scheduling` | clinics, appointments, queues and triage ordering |
| `clinical` | encounters (OPD/ED/IPD), vitals, notes, diagnoses, allergies, problem list, orders |
| `pharmacy` | formulary, stock batches and expiry, dispensing, controlled-drug register |
| `lab` | test catalogue, orders, specimens, results, critical values, quality control |
| `imaging` | radiology orders, reports signed by a second person, critical findings |
| `inpatient` | wards, beds, admission/transfer/discharge, nursing, theatre |
| `mch` | ANC, delivery, immunisation schedule, programme summary (postnatal and family planning not built) |
| `programmes` | HIV, TB and NCD registers, follow-up, outcomes, defaulter tracing |
| `billing` | price lists, charges, invoices, payers (cash, M-Pesa, SHA, insurers), receipts, eTIMS |
| `claims` | SHA claim builder and validator against the DHA eClaims FHIR guide, tracking, appeals |
| `reporting` | aggregate reports and configurable definitions with CSV and DHIS2 data value set export; the official MOH returns (705, 711, 731...) are not reproduced |
| `fhir` | read-only FHIR R4 interface; DHA/SHA adapters are unverified stubs |
| `portal` | the patient's own view: released results and reports, medicines, allergies, appointment requests (bills not included) |

## Data design for scale

- Clinical facts (`observation`, `audit_event`) are range-partitioned by month; patient and encounter
  tables are hash-partitioned by `org_id` once an org passes a size threshold.
- Reads that tolerate lag (reports, history) go to a replica; the write path uses the primary.
- Anything slow or external (claims submission, SMS, FHIR export) goes through a transactional
  outbox and a worker, never the request thread.
- Search uses `pg_trgm` and keyset paging; no list loads more than one page.

## Offline

Rural facilities lose power and links. What is built is deliberately narrow:

- A service worker keeps the app (scripts, styles, pages already opened) so the console opens without a connection, and a copy of
  the point-of-care records a person has read (queue, patient, encounter, maternal, programme, ward screens) for up to 12 hours.
  Anything sent with an access reason, anything about billing, claims, staff, reports or audit, and the patient portal are never
  kept. The copies are deleted on sign-out, and a screen served from a copy says so.
- Bedside capture (vitals, notes, antenatal and programme visits, immunisation doses) made while offline is kept in the browser's
  IndexedDB and sent when the connection returns, oldest first. Each entry carries an `Idempotency-Key`, and the server carries a
  write out once per key, so a resend after a dropped connection cannot double it. An entry the server refuses is kept as failed
  and shown on the Waiting to send screen; nothing is dropped silently.
- Not queued, on purpose: anything that needs a live decision (orders checked against allergies, payments, results, dispensing).
  Those need the connection.
- Entries waiting on a device are patient information at rest in that browser; the console warns when someone signs out with entries unsent.

## Phases

0. **Foundation:** tenancy + RLS, auth, RBAC, audit chain, consent, patient registry, practitioners.
1. **Outpatient care:** scheduling, triage, consultation, diagnoses, prescriptions, referrals.
2. **Ancillary and money:** pharmacy, lab, billing, M-Pesa, receipts.
3. **SHA and inpatient:** claims builder/validator/tracker, wards and beds, discharge, reporting.
4. **Programmes and reach:** maternal/immunisation, HIV/TB/NCD registers, imaging, configurable reports, FHIR read interface, patient portal, offline bedside capture (all built; see the README for what is not verified).

## What is not verified

SHA/DHA submission, the DHA certification process and its fee, and live ICD-11 licensing terms.
See `docs/OPEN-QUESTIONS.md` for the written questions to put to DHA and SHA.
