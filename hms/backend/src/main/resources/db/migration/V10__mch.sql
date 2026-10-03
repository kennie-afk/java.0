-- Maternal and child health: pregnancies with antenatal visits and delivery, and childhood immunisation.

CREATE TABLE pregnancies (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  lmp date NOT NULL,
  -- Naegele's rule: 280 days from the last menstrual period. Stored so a list can sort and filter on it.
  edd date NOT NULL,
  -- Gravida counts this pregnancy; parity counts earlier births, so it is always below gravida.
  gravida int NOT NULL CHECK (gravida BETWEEN 1 AND 20),
  parity int NOT NULL CHECK (parity >= 0),
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DELIVERED', 'LOST')),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  closed_at timestamptz,
  version int NOT NULL DEFAULT 1,
  UNIQUE (org_id, id),
  CHECK (edd = lmp + 280),
  CHECK (parity < gravida),
  CHECK ((status = 'ACTIVE') = (closed_at IS NULL)),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id)
);
-- One ongoing pregnancy per patient, whatever the API does.
CREATE UNIQUE INDEX pregnancies_one_active ON pregnancies (org_id, patient_id) WHERE status = 'ACTIVE';
CREATE INDEX pregnancies_facility ON pregnancies (org_id, facility_id, status, created_at DESC, id DESC);
CREATE INDEX pregnancies_patient ON pregnancies (org_id, patient_id, created_at DESC);

CREATE TABLE anc_visits (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  pregnancy_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  visit_number int NOT NULL CHECK (visit_number >= 1),
  visited_on date NOT NULL,
  gestation_days int NOT NULL CHECK (gestation_days >= 0),
  weight_kg numeric(5, 1) CHECK (weight_kg BETWEEN 20 AND 250),
  systolic int CHECK (systolic BETWEEN 50 AND 260),
  diastolic int CHECK (diastolic BETWEEN 30 AND 160),
  fundal_height_cm numeric(4, 1) CHECK (fundal_height_cm BETWEEN 0 AND 60),
  fetal_heart_rate int CHECK (fetal_heart_rate BETWEEN 60 AND 220),
  presentation text CHECK (presentation IN ('CEPHALIC', 'BREECH', 'TRANSVERSE', 'NOT_ASSESSED')),
  haemoglobin numeric(3, 1) CHECK (haemoglobin BETWEEN 2 AND 20),
  hiv_status text CHECK (hiv_status IN ('NEGATIVE', 'POSITIVE', 'KNOWN_POSITIVE', 'NOT_TESTED')),
  syphilis text CHECK (syphilis IN ('NEGATIVE', 'REACTIVE', 'NOT_TESTED')),
  urine_protein text CHECK (urine_protein IN ('NEGATIVE', 'TRACE', '1+', '2+', '3+', 'NOT_TESTED')),
  iptp_given boolean NOT NULL DEFAULT false,
  tetanus_given boolean NOT NULL DEFAULT false,
  iron_folate_given boolean NOT NULL DEFAULT false,
  -- Codes worked out by the server from the readings above; the client never supplies them.
  risk_flags text[] NOT NULL DEFAULT '{}',
  notes text,
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
CREATE INDEX anc_visits_pregnancy ON anc_visits (org_id, pregnancy_id, visit_number DESC);

CREATE TABLE deliveries (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  pregnancy_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  delivered_on date NOT NULL,
  gestation_days int NOT NULL CHECK (gestation_days >= 0),
  mode text NOT NULL CHECK (mode IN ('SVD', 'ASSISTED_VAGINAL', 'CAESAREAN', 'BREECH', 'NOT_APPLICABLE')),
  outcome text NOT NULL CHECK (outcome IN ('LIVE_BIRTH', 'STILLBIRTH', 'MISCARRIAGE')),
  babies int NOT NULL DEFAULT 1 CHECK (babies BETWEEN 0 AND 5),
  birth_weight_g int CHECK (birth_weight_g BETWEEN 200 AND 7000),
  apgar_5 int CHECK (apgar_5 BETWEEN 0 AND 10),
  blood_loss_ml int CHECK (blood_loss_ml BETWEEN 0 AND 10000),
  complications text,
  recorded_by uuid NOT NULL,
  recorded_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, pregnancy_id),
  FOREIGN KEY (org_id, pregnancy_id) REFERENCES pregnancies (org_id, id),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id)
);

CREATE TABLE immunisations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  -- A dose, not a vaccine: PENTA_2 is a different row from PENTA_1.
  vaccine text NOT NULL CHECK (length(vaccine) BETWEEN 2 AND 30),
  given_on date NOT NULL,
  batch_no text CHECK (length(batch_no) <= 60),
  site text CHECK (site IN ('LEFT_THIGH', 'RIGHT_THIGH', 'LEFT_ARM', 'RIGHT_ARM', 'ORAL')),
  recorded_by uuid NOT NULL,
  recorded_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, patient_id, vaccine),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id)
);
CREATE INDEX immunisations_patient ON immunisations (org_id, patient_id, given_on);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['pregnancies', 'anc_visits', 'deliveries', 'immunisations']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (org_id = current_org()) WITH CHECK (org_id = current_org())', t);
  END LOOP;
END $$;

-- Existing organisations get the permissions on their built-in roles; new ones get them from DefaultRoles.
UPDATE roles SET permissions = permissions || '["mch:read","mch:write"]'::jsonb
 WHERE is_system AND role_key IN ('DOCTOR', 'CLINICAL_OFFICER', 'NURSE') AND NOT permissions ? 'mch:read';
UPDATE roles SET permissions = permissions || '["mch:read"]'::jsonb
 WHERE is_system AND role_key = 'AUDITOR' AND NOT permissions ? 'mch:read';
