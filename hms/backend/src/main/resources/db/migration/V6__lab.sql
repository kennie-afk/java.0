-- Laboratory: test catalogue, orders, specimens and results with four-eyes validation and critical values.

UPDATE roles SET permissions = permissions || '["lab:manage"]'::jsonb
 WHERE is_system AND role_key = 'LAB_TECHNOLOGIST' AND NOT permissions ? 'lab:manage';

CREATE TABLE lab_tests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL REFERENCES organisations (id),
  code text NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9_.-]{0,29}$'),
  name text NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
  -- LOINC code where the organisation has mapped one; not validated against the LOINC table here.
  loinc_code text,
  specimen_type text NOT NULL DEFAULT 'BLOOD',
  result_type text NOT NULL DEFAULT 'NUMERIC' CHECK (result_type IN ('NUMERIC', 'TEXT')),
  unit text,
  ref_low numeric(14,4),
  ref_high numeric(14,4),
  critical_low numeric(14,4),
  critical_high numeric(14,4),
  price numeric(12,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id),
  UNIQUE (org_id, code),
  CHECK (ref_low IS NULL OR ref_high IS NULL OR ref_low <= ref_high),
  CHECK (critical_low IS NULL OR ref_low IS NULL OR critical_low <= ref_low),
  CHECK (critical_high IS NULL OR ref_high IS NULL OR critical_high >= ref_high)
);

CREATE TABLE lab_orders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  encounter_id uuid,
  order_number text NOT NULL,
  priority text NOT NULL DEFAULT 'ROUTINE' CHECK (priority IN ('ROUTINE', 'URGENT', 'STAT')),
  status text NOT NULL DEFAULT 'ORDERED' CHECK (status IN ('ORDERED', 'COLLECTED', 'RESULTED', 'VALIDATED', 'CANCELLED')),
  clinical_info text,
  ordered_by uuid NOT NULL,
  cancel_reason text,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id),
  UNIQUE (org_id, facility_id, order_number),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id),
  FOREIGN KEY (org_id, encounter_id) REFERENCES encounters (org_id, id),
  FOREIGN KEY (org_id, ordered_by) REFERENCES practitioners (org_id, id)
);
CREATE INDEX lab_orders_facility ON lab_orders (org_id, facility_id, status, created_at, id);
CREATE INDEX lab_orders_patient ON lab_orders (org_id, patient_id, created_at DESC);

CREATE TABLE lab_order_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  order_id uuid NOT NULL,
  test_id uuid NOT NULL,
  status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COLLECTED', 'RESULTED', 'VALIDATED', 'CANCELLED')),
  specimen_barcode text,
  collected_at timestamptz,
  collected_by uuid,
  result_numeric numeric(14,4),
  result_text text,
  flag text CHECK (flag IN ('N', 'L', 'H', 'LL', 'HH', 'A')),
  critical boolean NOT NULL DEFAULT false,
  entered_by uuid,
  entered_at timestamptz,
  validated_by uuid,
  validated_at timestamptz,
  critical_ack_by uuid,
  critical_ack_at timestamptz,
  critical_ack_note text,
  version int NOT NULL DEFAULT 0,
  UNIQUE (org_id, id),
  UNIQUE (order_id, test_id),
  FOREIGN KEY (org_id, order_id) REFERENCES lab_orders (org_id, id) ON DELETE CASCADE,
  FOREIGN KEY (org_id, test_id) REFERENCES lab_tests (org_id, id),
  -- Four eyes: the person who validates a result is never the person who entered it.
  CHECK (validated_by IS NULL OR validated_by <> entered_by)
);
CREATE INDEX lab_items_order ON lab_order_items (org_id, order_id);
CREATE INDEX lab_items_critical ON lab_order_items (org_id, entered_at) WHERE critical AND critical_ack_at IS NULL;
CREATE UNIQUE INDEX lab_specimen_barcode ON lab_order_items (org_id, specimen_barcode) WHERE specimen_barcode IS NOT NULL;

CREATE TABLE lab_result_history (
  id bigserial PRIMARY KEY,
  org_id uuid NOT NULL,
  item_id uuid NOT NULL,
  version int NOT NULL,
  result_numeric numeric(14,4),
  result_text text,
  flag text,
  entered_by uuid NOT NULL,
  entered_at timestamptz NOT NULL DEFAULT now(),
  reason text,
  FOREIGN KEY (org_id, item_id) REFERENCES lab_order_items (org_id, id) ON DELETE CASCADE,
  UNIQUE (item_id, version)
);
CREATE TRIGGER lab_result_history_no_update BEFORE UPDATE ON lab_result_history FOR EACH ROW EXECUTE FUNCTION forbid_change();

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['lab_tests', 'lab_orders', 'lab_order_items', 'lab_result_history']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (org_id = current_org()) WITH CHECK (org_id = current_org())', t);
  END LOOP;
END $$;
