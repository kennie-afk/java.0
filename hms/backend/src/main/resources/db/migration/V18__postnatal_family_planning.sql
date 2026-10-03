-- Postnatal care for the mother and baby after a closed pregnancy, and family planning visits. Both reuse the mch:read / mch:write
-- permissions, so no role needs updating.

CREATE TABLE postnatal_visits (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  pregnancy_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  visit_number int NOT NULL CHECK (visit_number >= 1),
  visited_on date NOT NULL,
  days_since_delivery int NOT NULL CHECK (days_since_delivery >= 0),
  -- The mother.
  systolic int CHECK (systolic BETWEEN 50 AND 260),
  diastolic int CHECK (diastolic BETWEEN 30 AND 160),
  temperature_c numeric(3, 1) CHECK (temperature_c BETWEEN 34 AND 42),
  uterus text CHECK (uterus IN ('INVOLUTING', 'SUBINVOLUTED', 'NOT_ASSESSED')),
  lochia text CHECK (lochia IN ('NORMAL', 'HEAVY', 'OFFENSIVE', 'NOT_ASSESSED')),
  wound text CHECK (wound IN ('HEALED', 'INFECTED', 'NOT_APPLICABLE', 'NOT_ASSESSED')),
  breastfeeding text CHECK (breastfeeding IN ('EXCLUSIVE', 'MIXED', 'NOT_BREASTFEEDING', 'NOT_ASSESSED')),
  low_mood boolean NOT NULL DEFAULT false,
  fp_counselled boolean NOT NULL DEFAULT false,
  -- The baby (only after a live birth).
  baby_weight_g int CHECK (baby_weight_g BETWEEN 500 AND 8000),
  baby_temperature_c numeric(3, 1) CHECK (baby_temperature_c BETWEEN 30 AND 42),
  cord text CHECK (cord IN ('CLEAN', 'INFECTED', 'SEPARATED', 'NOT_ASSESSED')),
  jaundice boolean,
  feeding_well boolean,
  -- Worked out by the server from the readings; the client never supplies them.
  risk_flags text[] NOT NULL DEFAULT '{}',
  notes text CHECK (length(notes) <= 2000),
  next_visit_on date,
  recorded_by uuid NOT NULL,
  recorded_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, pregnancy_id, visit_number),
  CHECK ((systolic IS NULL) = (diastolic IS NULL)),
  CHECK (systolic IS NULL OR systolic > diastolic),
  CHECK (next_visit_on IS NULL OR next_visit_on >= visited_on),
  FOREIGN KEY (org_id, pregnancy_id) REFERENCES pregnancies (org_id, id),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id)
);
CREATE INDEX postnatal_visits_pregnancy ON postnatal_visits (org_id, pregnancy_id, visit_number DESC);

CREATE TABLE family_planning_visits (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  visited_on date NOT NULL,
  visit_type text NOT NULL CHECK (visit_type IN ('NEW', 'REVISIT', 'SWITCH', 'DISCONTINUE')),
  -- The method the patient leaves with. NONE records that they stopped.
  method text NOT NULL CHECK (method IN ('COC', 'POP', 'DMPA', 'IMPLANT', 'IUCD', 'MALE_CONDOM', 'FEMALE_CONDOM',
                                         'TUBAL_LIGATION', 'VASECTOMY', 'NATURAL', 'EMERGENCY', 'NONE')),
  systolic int CHECK (systolic BETWEEN 50 AND 260),
  diastolic int CHECK (diastolic BETWEEN 30 AND 160),
  weight_kg numeric(5, 1) CHECK (weight_kg BETWEEN 20 AND 250),
  next_due_on date,
  risk_flags text[] NOT NULL DEFAULT '{}',
  notes text CHECK (length(notes) <= 2000),
  recorded_by uuid NOT NULL,
  recorded_at timestamptz NOT NULL DEFAULT now(),
  CHECK ((visit_type = 'DISCONTINUE') = (method = 'NONE')),
  CHECK ((systolic IS NULL) = (diastolic IS NULL)),
  CHECK (systolic IS NULL OR systolic > diastolic),
  CHECK (next_due_on IS NULL OR next_due_on >= visited_on),
  CHECK (method NOT IN ('TUBAL_LIGATION', 'VASECTOMY', 'NONE') OR next_due_on IS NULL),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id)
);
CREATE INDEX fp_visits_patient ON family_planning_visits (org_id, patient_id, visited_on DESC, recorded_at DESC);
CREATE INDEX fp_visits_due ON family_planning_visits (org_id, facility_id, next_due_on) WHERE next_due_on IS NOT NULL;

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['postnatal_visits', 'family_planning_visits']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (org_id = current_org()) WITH CHECK (org_id = current_org())', t);
  END LOOP;
END $$;

-- Visits are a record of what was seen; a mistake is corrected by a later visit, not by editing history.
CREATE TRIGGER postnatal_visits_append_only BEFORE UPDATE OR DELETE ON postnatal_visits FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER family_planning_visits_append_only BEFORE UPDATE OR DELETE ON family_planning_visits FOR EACH ROW EXECUTE FUNCTION forbid_change();
