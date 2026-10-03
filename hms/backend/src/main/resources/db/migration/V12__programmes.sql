-- Long-term care programmes (HIV, TB, hypertension, diabetes, asthma, epilepsy): enrolment registers, follow-up visits, defaulter tracing.
-- Locally configured registers for follow-up, not the Ministry of Health's official registers or indicator definitions.

CREATE TABLE programme_enrolments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  programme text NOT NULL CHECK (programme IN ('HIV', 'TB', 'HYPERTENSION', 'DIABETES', 'ASTHMA', 'EPILEPSY')),
  register_no text NOT NULL,
  enrolled_on date NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'TRANSFERRED_OUT', 'LOST_TO_FOLLOW_UP', 'COMPLETED', 'DIED', 'STOPPED')),
  regimen text CHECK (length(regimen) <= 200),
  next_visit_on date,
  outcome_on date,
  outcome_note text CHECK (length(outcome_note) <= 500),
  enrolled_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  version int NOT NULL DEFAULT 1,
  UNIQUE (org_id, id),
  UNIQUE (org_id, facility_id, programme, register_no),
  CHECK ((status = 'ACTIVE') = (outcome_on IS NULL)),
  CHECK (outcome_on IS NULL OR outcome_on >= enrolled_on),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id)
);
-- A person is in a given programme once at a time; after an outcome they can be enrolled again.
CREATE UNIQUE INDEX programme_one_active ON programme_enrolments (org_id, patient_id, programme) WHERE status = 'ACTIVE';
CREATE INDEX programme_enrolments_facility ON programme_enrolments (org_id, facility_id, programme, status, created_at DESC, id);
CREATE INDEX programme_enrolments_due ON programme_enrolments (org_id, facility_id, next_visit_on) WHERE status = 'ACTIVE';
CREATE INDEX programme_enrolments_patient ON programme_enrolments (org_id, patient_id);

CREATE TABLE programme_visits (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  enrolment_id uuid NOT NULL,
  visited_on date NOT NULL,
  weight_kg numeric(5,1) CHECK (weight_kg BETWEEN 0.3 AND 400),
  systolic int CHECK (systolic BETWEEN 40 AND 300),
  diastolic int CHECK (diastolic BETWEEN 20 AND 200),
  glucose_mmol numeric(4,1) CHECK (glucose_mmol BETWEEN 0.5 AND 60),
  adherence text CHECK (adherence IN ('GOOD', 'FAIR', 'POOR')),
  regimen text CHECK (length(regimen) <= 200),
  next_visit_on date,
  notes text CHECK (length(notes) <= 1000),
  recorded_by uuid NOT NULL,
  recorded_at timestamptz NOT NULL DEFAULT now(),
  CHECK (next_visit_on IS NULL OR next_visit_on > visited_on),
  CHECK ((systolic IS NULL) = (diastolic IS NULL)),
  FOREIGN KEY (org_id, enrolment_id) REFERENCES programme_enrolments (org_id, id)
);
CREATE INDEX programme_visits_enrolment ON programme_visits (org_id, enrolment_id, visited_on DESC);
CREATE TRIGGER programme_visits_no_update BEFORE UPDATE ON programme_visits FOR EACH ROW EXECUTE FUNCTION forbid_change();

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['programme_enrolments', 'programme_visits']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (org_id = current_org()) WITH CHECK (org_id = current_org())', t);
  END LOOP;
END $$;

-- HIV care is stigmatised, so it has its own permission on top of the general one.
UPDATE roles SET permissions = permissions || '["programmes:read","programmes:write","programmes:hiv"]'::jsonb
 WHERE is_system AND role_key IN ('DOCTOR', 'CLINICAL_OFFICER', 'NURSE') AND NOT permissions ? 'programmes:read';
UPDATE roles SET permissions = permissions || '["programmes:read"]'::jsonb
 WHERE is_system AND role_key = 'AUDITOR' AND NOT permissions ? 'programmes:read';
