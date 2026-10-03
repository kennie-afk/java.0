-- Imaging (radiology): procedure catalogue, orders, performed study, report with four-eyes sign-off and critical findings.

CREATE TABLE imaging_procedures (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL REFERENCES organisations (id),
  code text NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9_.-]{0,29}$'),
  name text NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
  -- DICOM-style two letter modality codes for the common ones; OTHER for the rest.
  modality text NOT NULL CHECK (modality IN ('XR', 'US', 'CT', 'MR', 'MG', 'FL', 'NM', 'OTHER')),
  body_region text CHECK (length(body_region) <= 80),
  price numeric(12,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id),
  UNIQUE (org_id, code)
);

CREATE TABLE imaging_orders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  encounter_id uuid,
  procedure_id uuid NOT NULL,
  order_number text NOT NULL,
  priority text NOT NULL DEFAULT 'ROUTINE' CHECK (priority IN ('ROUTINE', 'URGENT', 'STAT')),
  status text NOT NULL DEFAULT 'ORDERED' CHECK (status IN ('ORDERED', 'PERFORMED', 'REPORTED', 'SIGNED', 'CANCELLED')),
  clinical_info text CHECK (length(clinical_info) <= 500),
  ordered_by uuid NOT NULL,
  cancel_reason text,
  performed_by uuid,
  performed_at timestamptz,
  technique_note text CHECK (length(technique_note) <= 500),
  findings text CHECK (length(findings) <= 8000),
  impression text CHECK (length(impression) <= 2000),
  critical boolean NOT NULL DEFAULT false,
  critical_note text,
  reported_by uuid,
  reported_at timestamptz,
  signed_by uuid,
  signed_at timestamptz,
  critical_ack_by uuid,
  critical_ack_at timestamptz,
  critical_ack_note text,
  version int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id),
  UNIQUE (org_id, facility_id, order_number),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id),
  FOREIGN KEY (org_id, encounter_id) REFERENCES encounters (org_id, id),
  FOREIGN KEY (org_id, procedure_id) REFERENCES imaging_procedures (org_id, id),
  FOREIGN KEY (org_id, ordered_by) REFERENCES practitioners (org_id, id),
  -- Four eyes: the person who signs a report is never the person who wrote it.
  CHECK (signed_by IS NULL OR signed_by <> reported_by),
  CHECK (NOT critical OR critical_note IS NOT NULL)
);
CREATE INDEX imaging_orders_facility ON imaging_orders (org_id, facility_id, status, created_at, id);
CREATE INDEX imaging_orders_patient ON imaging_orders (org_id, patient_id, created_at DESC);
CREATE INDEX imaging_orders_critical ON imaging_orders (org_id, reported_at) WHERE critical AND critical_ack_at IS NULL;

CREATE TABLE imaging_report_history (
  id bigserial PRIMARY KEY,
  org_id uuid NOT NULL,
  order_id uuid NOT NULL,
  version int NOT NULL,
  findings text,
  impression text,
  critical boolean NOT NULL,
  reported_by uuid NOT NULL,
  reported_at timestamptz NOT NULL DEFAULT now(),
  reason text,
  FOREIGN KEY (org_id, order_id) REFERENCES imaging_orders (org_id, id) ON DELETE CASCADE,
  UNIQUE (order_id, version)
);
CREATE TRIGGER imaging_report_history_no_update BEFORE UPDATE ON imaging_report_history FOR EACH ROW EXECUTE FUNCTION forbid_change();

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['imaging_procedures', 'imaging_orders', 'imaging_report_history']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (org_id = current_org()) WITH CHECK (org_id = current_org())', t);
  END LOOP;
END $$;

-- Existing organisations: permissions on built-in roles, and the two new imaging roles. New ones get them from DefaultRoles.
UPDATE roles SET permissions = permissions || '["imaging:read"]'::jsonb
 WHERE is_system AND role_key IN ('DOCTOR', 'CLINICAL_OFFICER', 'NURSE', 'AUDITOR') AND NOT permissions ? 'imaging:read';
INSERT INTO roles (org_id, role_key, label, description, is_system, permissions)
SELECT id, 'RADIOGRAPHER', 'Radiographer', 'Performs studies and drafts reports', true, '["patients:read","clinical:read","imaging:read","imaging:perform"]'::jsonb
  FROM organisations ON CONFLICT (org_id, role_key) DO NOTHING;
INSERT INTO roles (org_id, role_key, label, description, is_system, permissions)
SELECT id, 'RADIOLOGIST', 'Radiologist', 'Reports and signs imaging studies', true, '["patients:read","clinical:read","imaging:read","imaging:perform","imaging:sign","imaging:manage"]'::jsonb
  FROM organisations ON CONFLICT (org_id, role_key) DO NOTHING;
