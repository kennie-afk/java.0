-- Pharmacy: formulary, stock batches with expiry, a stock ledger, dispensing against medication orders.

CREATE TABLE drugs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL REFERENCES organisations (id),
  generic_name text NOT NULL CHECK (length(generic_name) BETWEEN 2 AND 200),
  strength text,
  form text NOT NULL CHECK (length(form) BETWEEN 2 AND 60),
  unit text NOT NULL DEFAULT 'unit' CHECK (length(unit) BETWEEN 1 AND 30),
  -- Pharmacy and Poisons Board product code and WHO ATC code, entered by the organisation when it has them.
  ppb_code text,
  atc_code text,
  controlled boolean NOT NULL DEFAULT false,
  unit_price numeric(12,2) NOT NULL DEFAULT 0 CHECK (unit_price >= 0),
  reorder_level numeric(12,2) NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id)
);
CREATE UNIQUE INDEX drugs_unique_product ON drugs (org_id, lower(generic_name), coalesce(lower(strength), ''), lower(form));
CREATE INDEX drugs_name ON drugs (org_id, lower(generic_name), id);

ALTER TABLE orders ADD CONSTRAINT orders_drug_fk FOREIGN KEY (org_id, drug_id) REFERENCES drugs (org_id, id);

CREATE TABLE stock_batches (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  drug_id uuid NOT NULL,
  batch_no text NOT NULL CHECK (length(batch_no) BETWEEN 1 AND 60),
  expiry_date date NOT NULL,
  quantity numeric(12,2) NOT NULL CHECK (quantity >= 0),
  unit_cost numeric(12,2) NOT NULL DEFAULT 0 CHECK (unit_cost >= 0),
  supplier text,
  received_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id),
  UNIQUE (org_id, facility_id, drug_id, batch_no),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id),
  FOREIGN KEY (org_id, drug_id) REFERENCES drugs (org_id, id)
);
-- First-expiry-first-out reads batches in this order.
CREATE INDEX stock_batches_fefo ON stock_batches (org_id, facility_id, drug_id, expiry_date, id) WHERE quantity > 0;

-- Every change in stock, forever: the register an inspector or auditor reads.
CREATE TABLE stock_movements (
  id bigserial PRIMARY KEY,
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  drug_id uuid NOT NULL,
  batch_id uuid NOT NULL,
  delta numeric(12,2) NOT NULL CHECK (delta <> 0),
  reason text NOT NULL CHECK (reason IN ('RECEIPT', 'DISPENSE', 'ADJUSTMENT', 'WRITE_OFF', 'RETURN')),
  ref_type text,
  ref_id uuid,
  note text,
  practitioner_id uuid NOT NULL,
  witness_id uuid,
  at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (org_id, batch_id) REFERENCES stock_batches (org_id, id),
  FOREIGN KEY (org_id, drug_id) REFERENCES drugs (org_id, id)
);
CREATE INDEX stock_movements_drug ON stock_movements (org_id, facility_id, drug_id, id DESC);
CREATE TRIGGER stock_movements_append_only BEFORE UPDATE OR DELETE ON stock_movements FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TABLE dispensings (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  order_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  drug_id uuid NOT NULL,
  quantity numeric(12,2) NOT NULL CHECK (quantity > 0),
  dispensed_by uuid NOT NULL,
  witness_id uuid,
  allergy_override_reason text,
  dispensed_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id),
  FOREIGN KEY (org_id, order_id) REFERENCES orders (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id),
  FOREIGN KEY (org_id, drug_id) REFERENCES drugs (org_id, id),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id)
);
CREATE INDEX dispensings_order ON dispensings (org_id, order_id);
CREATE INDEX dispensings_patient ON dispensings (org_id, patient_id, dispensed_at DESC);
CREATE TRIGGER dispensings_append_only BEFORE UPDATE OR DELETE ON dispensings FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TABLE dispensing_lines (
  id bigserial PRIMARY KEY,
  org_id uuid NOT NULL,
  dispensing_id uuid NOT NULL,
  batch_id uuid NOT NULL,
  quantity numeric(12,2) NOT NULL CHECK (quantity > 0),
  unit_cost numeric(12,2) NOT NULL,
  FOREIGN KEY (org_id, dispensing_id) REFERENCES dispensings (org_id, id),
  FOREIGN KEY (org_id, batch_id) REFERENCES stock_batches (org_id, id)
);
CREATE TRIGGER dispensing_lines_append_only BEFORE UPDATE OR DELETE ON dispensing_lines FOR EACH ROW EXECUTE FUNCTION forbid_change();

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['drugs', 'stock_batches', 'stock_movements', 'dispensings', 'dispensing_lines']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (org_id = current_org()) WITH CHECK (org_id = current_org())', t);
  END LOOP;
END $$;
