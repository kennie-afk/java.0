-- Clinical: encounters, vitals, notes, diagnoses (ICD-11), allergies, orders.
-- Clinical facts are append-only: a correction is a new row that supersedes, never an overwrite.

CREATE TABLE encounters (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  encounter_type text NOT NULL CHECK (encounter_type IN ('OPD', 'ED', 'IPD')),
  status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
  attending_id uuid,
  appointment_id uuid,
  chief_complaint text,
  triage_category text CHECK (triage_category IN ('EMERGENCY', 'PRIORITY', 'ROUTINE')),
  triaged_by uuid,
  triaged_at timestamptz,
  started_at timestamptz NOT NULL DEFAULT now(),
  ended_at timestamptz,
  closed_by uuid,
  version int NOT NULL DEFAULT 1,
  UNIQUE (org_id, id),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id),
  FOREIGN KEY (org_id, attending_id) REFERENCES practitioners (org_id, id),
  FOREIGN KEY (org_id, appointment_id) REFERENCES appointments (org_id, id)
);
-- A patient has one open encounter of each kind at a facility; a second is a duplicate.
CREATE UNIQUE INDEX encounters_one_open ON encounters (org_id, patient_id, facility_id, encounter_type) WHERE status = 'OPEN';
CREATE INDEX encounters_patient ON encounters (org_id, patient_id, started_at DESC, id);
CREATE INDEX encounters_facility_open ON encounters (org_id, facility_id, started_at) WHERE status = 'OPEN';

CREATE TABLE vitals (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  encounter_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  recorded_by uuid NOT NULL,
  recorded_at timestamptz NOT NULL DEFAULT now(),
  temp_c numeric(4,1) CHECK (temp_c BETWEEN 25 AND 45),
  pulse int CHECK (pulse BETWEEN 0 AND 300),
  resp_rate int CHECK (resp_rate BETWEEN 0 AND 100),
  systolic int CHECK (systolic BETWEEN 20 AND 350),
  diastolic int CHECK (diastolic BETWEEN 10 AND 250),
  spo2 int CHECK (spo2 BETWEEN 0 AND 100),
  weight_kg numeric(5,1) CHECK (weight_kg BETWEEN 0.2 AND 700),
  height_cm numeric(5,1) CHECK (height_cm BETWEEN 20 AND 280),
  muac_cm numeric(4,1) CHECK (muac_cm BETWEEN 3 AND 60),
  glucose_mmol numeric(4,1) CHECK (glucose_mmol BETWEEN 0.5 AND 100),
  pain_score int CHECK (pain_score BETWEEN 0 AND 10),
  CHECK (temp_c IS NOT NULL OR pulse IS NOT NULL OR resp_rate IS NOT NULL OR systolic IS NOT NULL OR spo2 IS NOT NULL OR weight_kg IS NOT NULL
         OR height_cm IS NOT NULL OR muac_cm IS NOT NULL OR glucose_mmol IS NOT NULL OR pain_score IS NOT NULL),
  CHECK ((systolic IS NULL) = (diastolic IS NULL)),
  UNIQUE (org_id, id),
  FOREIGN KEY (org_id, encounter_id) REFERENCES encounters (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id),
  FOREIGN KEY (org_id, recorded_by) REFERENCES practitioners (org_id, id)
);
CREATE INDEX vitals_encounter ON vitals (org_id, encounter_id, recorded_at DESC);
CREATE INDEX vitals_patient ON vitals (org_id, patient_id, recorded_at DESC);
CREATE TRIGGER vitals_append_only BEFORE UPDATE OR DELETE ON vitals FOR EACH ROW EXECUTE FUNCTION forbid_change();

-- Saying "this entry was wrong" without touching the entry itself.
CREATE TABLE clinical_retractions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  entity_type text NOT NULL CHECK (entity_type IN ('vitals')),
  entity_id uuid NOT NULL,
  reason text NOT NULL CHECK (length(reason) >= 5),
  retracted_by uuid NOT NULL,
  retracted_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, entity_type, entity_id)
);
CREATE TRIGGER clinical_retractions_append_only BEFORE UPDATE OR DELETE ON clinical_retractions FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TABLE clinical_notes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  encounter_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  thread_id uuid NOT NULL,
  version int NOT NULL DEFAULT 1,
  kind text NOT NULL CHECK (kind IN ('SOAP', 'PROGRESS', 'ADMISSION', 'DISCHARGE', 'PROCEDURE', 'NURSING', 'OTHER')),
  body text NOT NULL CHECK (length(body) BETWEEN 1 AND 20000),
  author_id uuid NOT NULL,
  amend_reason text,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK ((version = 1) = (amend_reason IS NULL)),
  UNIQUE (org_id, thread_id, version),
  FOREIGN KEY (org_id, encounter_id) REFERENCES encounters (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id),
  FOREIGN KEY (org_id, author_id) REFERENCES practitioners (org_id, id)
);
CREATE INDEX clinical_notes_encounter ON clinical_notes (org_id, encounter_id, created_at);
CREATE TRIGGER clinical_notes_append_only BEFORE UPDATE OR DELETE ON clinical_notes FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TABLE diagnoses (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  encounter_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  -- ICD-11 MMS code. Only the SHAPE is checked: this system holds no licensed ICD-11 catalogue.
  icd11_code text NOT NULL CHECK (icd11_code ~ '^[0-9A-Z]{4}(\.[0-9A-Z]{1,2})?$'),
  title text NOT NULL CHECK (length(title) BETWEEN 2 AND 300),
  kind text NOT NULL DEFAULT 'SECONDARY' CHECK (kind IN ('PRIMARY', 'SECONDARY')),
  certainty text NOT NULL DEFAULT 'PROVISIONAL' CHECK (certainty IN ('PROVISIONAL', 'CONFIRMED', 'RULED_OUT')),
  recorded_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, encounter_id, icd11_code),
  FOREIGN KEY (org_id, encounter_id) REFERENCES encounters (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id)
);
CREATE UNIQUE INDEX diagnoses_one_primary ON diagnoses (org_id, encounter_id) WHERE kind = 'PRIMARY' AND certainty <> 'RULED_OUT';
CREATE INDEX diagnoses_patient ON diagnoses (org_id, patient_id, created_at DESC);
CREATE INDEX diagnoses_code ON diagnoses (org_id, facility_id, icd11_code, created_at);

CREATE TABLE allergies (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  substance text NOT NULL CHECK (length(substance) BETWEEN 2 AND 120),
  category text NOT NULL DEFAULT 'DRUG' CHECK (category IN ('DRUG', 'FOOD', 'ENVIRONMENT', 'OTHER')),
  reaction text,
  severity text NOT NULL CHECK (severity IN ('MILD', 'MODERATE', 'SEVERE', 'LIFE_THREATENING')),
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'ENTERED_IN_ERROR')),
  status_reason text,
  recorded_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id)
);
CREATE INDEX allergies_patient ON allergies (org_id, patient_id);

CREATE TABLE orders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  encounter_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  kind text NOT NULL CHECK (kind IN ('MEDICATION', 'PROCEDURE', 'IMAGING', 'REFERRAL', 'OTHER')),
  status text NOT NULL DEFAULT 'ORDERED' CHECK (status IN ('ORDERED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
  priority text NOT NULL DEFAULT 'ROUTINE' CHECK (priority IN ('ROUTINE', 'URGENT', 'STAT')),
  description text NOT NULL CHECK (length(description) BETWEEN 2 AND 500),
  -- Medication detail (kind = MEDICATION). drug_id links to the formulary once pharmacy exists.
  drug_id uuid,
  drug_name text,
  dose text,
  route text,
  frequency text,
  duration_days int CHECK (duration_days BETWEEN 1 AND 365),
  quantity numeric(10,2) CHECK (quantity > 0),
  dispensed_quantity numeric(10,2) NOT NULL DEFAULT 0 CHECK (dispensed_quantity >= 0),
  instructions text,
  allergy_override_reason text,
  ordered_by uuid NOT NULL,
  cancel_reason text,
  version int NOT NULL DEFAULT 1,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id),
  CHECK (kind <> 'MEDICATION' OR (drug_name IS NOT NULL AND quantity IS NOT NULL)),
  CHECK (dispensed_quantity <= coalesce(quantity, dispensed_quantity)),
  FOREIGN KEY (org_id, encounter_id) REFERENCES encounters (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id),
  FOREIGN KEY (org_id, ordered_by) REFERENCES practitioners (org_id, id)
);
CREATE INDEX orders_encounter ON orders (org_id, encounter_id, created_at);
CREATE INDEX orders_pending ON orders (org_id, facility_id, kind, created_at) WHERE status IN ('ORDERED', 'IN_PROGRESS');

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['encounters', 'vitals', 'clinical_retractions', 'clinical_notes', 'diagnoses', 'allergies', 'orders']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (org_id = current_org()) WITH CHECK (org_id = current_org())', t);
  END LOOP;
END $$;
