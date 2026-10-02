-- Billing: price list, invoices (frozen once issued), payments (cash, M-Pesa, card, bank), receipts.

CREATE TABLE charge_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL REFERENCES organisations (id),
  code text NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9_.-]{0,29}$'),
  name text NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
  category text NOT NULL CHECK (category IN ('CONSULTATION', 'LAB', 'PHARMACY', 'IMAGING', 'PROCEDURE', 'BED', 'OTHER')),
  price numeric(12,2) NOT NULL CHECK (price >= 0),
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id),
  UNIQUE (org_id, code)
);

CREATE TABLE invoices (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  encounter_id uuid,
  invoice_number text NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'VOID')),
  payer_type text NOT NULL DEFAULT 'CASH' CHECK (payer_type IN ('CASH', 'SHA', 'INSURER')),
  payer_name text,
  total numeric(12,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
  amount_paid numeric(12,2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),
  currency text NOT NULL DEFAULT 'KES' CHECK (currency = 'KES'),
  issued_at timestamptz,
  void_reason text,
  created_by uuid NOT NULL,
  version int NOT NULL DEFAULT 1,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id),
  UNIQUE (org_id, facility_id, invoice_number),
  CHECK (amount_paid <= total),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id),
  FOREIGN KEY (org_id, encounter_id) REFERENCES encounters (org_id, id)
);
CREATE INDEX invoices_facility ON invoices (org_id, facility_id, created_at DESC, id DESC);
CREATE INDEX invoices_patient ON invoices (org_id, patient_id, created_at DESC);
CREATE INDEX invoices_open ON invoices (org_id, facility_id, status) WHERE status IN ('ISSUED', 'PARTIALLY_PAID');

CREATE TABLE invoice_lines (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  invoice_id uuid NOT NULL,
  description text NOT NULL CHECK (length(description) BETWEEN 2 AND 300),
  source_type text NOT NULL DEFAULT 'MANUAL' CHECK (source_type IN ('MANUAL', 'CHARGE', 'LAB', 'DISPENSING', 'BED')),
  source_id uuid,
  quantity numeric(10,2) NOT NULL CHECK (quantity > 0),
  unit_price numeric(12,2) NOT NULL CHECK (unit_price >= 0),
  line_total numeric(12,2) GENERATED ALWAYS AS (round(quantity * unit_price, 2)) STORED,
  FOREIGN KEY (org_id, invoice_id) REFERENCES invoices (org_id, id) ON DELETE CASCADE
);
CREATE INDEX invoice_lines_invoice ON invoice_lines (org_id, invoice_id);

-- Each dispensing or lab item is billed at most once, whichever invoice it landed on. Voiding releases it.
CREATE TABLE billed_sources (
  org_id uuid NOT NULL,
  source_type text NOT NULL,
  source_id uuid NOT NULL,
  invoice_id uuid NOT NULL,
  PRIMARY KEY (org_id, source_type, source_id),
  FOREIGN KEY (org_id, invoice_id) REFERENCES invoices (org_id, id) ON DELETE CASCADE
);

-- Once an invoice leaves DRAFT its lines are frozen: the database refuses the change, not just the API.
CREATE FUNCTION invoice_lines_frozen() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s text;
BEGIN
  SELECT status INTO s FROM invoices WHERE id = COALESCE(NEW.invoice_id, OLD.invoice_id);
  IF s IS DISTINCT FROM 'DRAFT' THEN
    RAISE EXCEPTION 'invoice lines can only change while the invoice is a draft';
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$;
CREATE TRIGGER invoice_lines_frozen BEFORE INSERT OR UPDATE OR DELETE ON invoice_lines FOR EACH ROW EXECUTE FUNCTION invoice_lines_frozen();

CREATE TABLE payments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  invoice_id uuid NOT NULL,
  method text NOT NULL CHECK (method IN ('CASH', 'MPESA', 'CARD', 'BANK')),
  amount numeric(12,2) NOT NULL CHECK (amount > 0),
  status text NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED')),
  reference text,
  idempotency_key text,
  mpesa_phone text,
  mpesa_checkout_id text,
  mpesa_receipt text,
  failure_reason text,
  received_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  reversed_by uuid,
  reversed_at timestamptz,
  reversal_reason text,
  UNIQUE (org_id, id),
  FOREIGN KEY (org_id, invoice_id) REFERENCES invoices (org_id, id),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id)
);
-- The same client retry returns the same payment instead of charging twice.
CREATE UNIQUE INDEX payments_idempotency ON payments (org_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX payments_mpesa_checkout ON payments (org_id, mpesa_checkout_id) WHERE mpesa_checkout_id IS NOT NULL;
CREATE UNIQUE INDEX payments_mpesa_receipt ON payments (org_id, mpesa_receipt) WHERE mpesa_receipt IS NOT NULL;
CREATE INDEX payments_invoice ON payments (org_id, invoice_id);
CREATE TRIGGER payments_no_delete BEFORE DELETE ON payments FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TABLE receipts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  payment_id uuid NOT NULL,
  receipt_number text NOT NULL,
  issued_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, payment_id),
  UNIQUE (org_id, facility_id, receipt_number),
  FOREIGN KEY (org_id, payment_id) REFERENCES payments (org_id, id)
);
CREATE TRIGGER receipts_append_only BEFORE UPDATE OR DELETE ON receipts FOR EACH ROW EXECUTE FUNCTION forbid_change();

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['charge_items', 'invoices', 'invoice_lines', 'billed_sources', 'payments', 'receipts']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (org_id = current_org()) WITH CHECK (org_id = current_org())', t);
  END LOOP;
END $$;
