-- Inpatient: wards, beds, admissions, bed assignments (transfers) and discharge.

CREATE TABLE wards (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  name text NOT NULL CHECK (length(name) BETWEEN 2 AND 100),
  kind text NOT NULL DEFAULT 'GENERAL' CHECK (kind IN ('GENERAL', 'SURGICAL', 'MEDICAL', 'MATERNITY', 'PAEDIATRIC', 'NEWBORN', 'ICU', 'HDU', 'ISOLATION', 'PSYCHIATRIC', 'OTHER')),
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id),
  UNIQUE (org_id, facility_id, name),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id)
);

CREATE TABLE beds (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  ward_id uuid NOT NULL,
  label text NOT NULL CHECK (length(label) BETWEEN 1 AND 30),
  status text NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'CLEANING', 'OUT_OF_SERVICE')),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id),
  UNIQUE (org_id, ward_id, label),
  FOREIGN KEY (org_id, ward_id) REFERENCES wards (org_id, id),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id)
);
CREATE INDEX beds_ward ON beds (org_id, ward_id, status);

CREATE TABLE admissions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  encounter_id uuid NOT NULL,
  admission_number text NOT NULL,
  status text NOT NULL DEFAULT 'ADMITTED' CHECK (status IN ('ADMITTED', 'DISCHARGED')),
  admitting_diagnosis text,
  admitted_at timestamptz NOT NULL DEFAULT now(),
  admitted_by uuid NOT NULL,
  discharge_type text CHECK (discharge_type IN ('DISCHARGED', 'REFERRED', 'LAMA', 'ABSCONDED', 'DIED')),
  discharge_summary text,
  discharged_at timestamptz,
  discharged_by uuid,
  version int NOT NULL DEFAULT 1,
  UNIQUE (org_id, id),
  UNIQUE (org_id, facility_id, admission_number),
  CHECK ((status = 'ADMITTED') = (discharged_at IS NULL)),
  CHECK ((status = 'DISCHARGED') = (discharge_type IS NOT NULL)),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id),
  FOREIGN KEY (org_id, encounter_id) REFERENCES encounters (org_id, id)
);
-- A patient is in one bed in one place at a time.
CREATE UNIQUE INDEX admissions_one_active ON admissions (org_id, patient_id) WHERE status = 'ADMITTED';
CREATE INDEX admissions_facility ON admissions (org_id, facility_id, status, admitted_at DESC, id DESC);
CREATE INDEX admissions_patient ON admissions (org_id, patient_id, admitted_at DESC);

CREATE TABLE bed_assignments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  admission_id uuid NOT NULL,
  bed_id uuid NOT NULL,
  assigned_at timestamptz NOT NULL DEFAULT now(),
  released_at timestamptz,
  reason text,
  assigned_by uuid NOT NULL,
  CHECK (released_at IS NULL OR released_at >= assigned_at),
  FOREIGN KEY (org_id, admission_id) REFERENCES admissions (org_id, id),
  FOREIGN KEY (org_id, bed_id) REFERENCES beds (org_id, id)
);
-- One occupant per bed, one bed per admission, enforced by the database whatever the API does.
CREATE UNIQUE INDEX bed_assignments_one_occupant ON bed_assignments (org_id, bed_id) WHERE released_at IS NULL;
CREATE UNIQUE INDEX bed_assignments_one_bed ON bed_assignments (org_id, admission_id) WHERE released_at IS NULL;
CREATE INDEX bed_assignments_admission ON bed_assignments (org_id, admission_id, assigned_at);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['wards', 'beds', 'admissions', 'bed_assignments']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (org_id = current_org()) WITH CHECK (org_id = current_org())', t);
  END LOOP;
END $$;
