# FHIR R4 read interface

`/fhir/r4`, JSON (`application/fhir+json`), read and search only. Source: `backend/src/main/java/com/hms/fhir`.

| Resource | Read | Search | Source |
|---|---|---|---|
| Patient | `/Patient/{id}` | `identifier` (`value` or `urn:hms:identifier:<TYPE>\|value`), `name`, `birthdate`, `_id` | registry |
| Encounter | `/Encounter/{id}` | `patient` | encounters |
| Observation | `/Observation/{id}` | `patient` and `category` (`vital-signs` or `laboratory`, required) | vitals; **validated** lab results only |
| MedicationRequest | `/MedicationRequest/{id}` | `patient` | medication orders |
| AllergyIntolerance | `/AllergyIntolerance/{id}` | `patient` | allergy list |

Paging: `_count` (default 20, max 100) and a `next` link carrying an opaque `_cursor`. For vital signs a page is `_count` recordings (one recording can hold several observations).

Security: the caller needs `fhir:read` plus the underlying read permissions (the built-in role *Integration (FHIR read)* has them). Every read and search is audited. A restricted record is excluded from search and needs `X-Access-Reason` on read. Unvalidated lab results and retracted vitals are never returned. Errors are `OperationOutcome` resources.

Honest limits: the mapping follows the public FHIR R4 (4.0.1) spec and LOINC / UCUM / HL7 terminology codes; MUAC has no code because none was verified. Identifiers use a local urn, not any national namespace. It is not claimed to conform to a national implementation guide and has not been run through the HL7 validator. No create, update or delete exists.
