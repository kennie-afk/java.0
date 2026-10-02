-- Claims readiness (Madai): assemble a claim from the clinical and billing record, validate it against
-- readiness rules, and hand it to an adapter. No live SHA/DHA connection exists or is claimed.

CREATE TABLE claims (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  encounter_id uuid NOT NULL,
  invoice_id uuid NOT NULL,
  claim_number text NOT NULL,
  payer_type text NOT NULL CHECK (payer_type IN ('SHA', 'INSURER')),
  -- SUBMITTED, ACCEPTED, REJECTED and PAID are reserved for a verified adapter and are unreachable today.
  status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN
    ('DRAFT', 'NEEDS_ATTENTION', 'READY', 'SUBMISSION_STUBBED', 'SUBMITTED', 'ACCEPTED', 'REJECTED', 'PAID', 'WITHDRAWN')),
  total numeric(12,2) NOT NULL CHECK (total >= 0),
  -- A snapshot of what the claim would say, in HMS's own internal shape (not a DHA FHIR resource).
  bundle jsonb NOT NULL,
  bundle_hash bytea NOT NULL,
  assembled_by uuid NOT NULL,
  assembled_at timestamptz NOT NULL DEFAULT now(),
  withdraw_reason text,
  version int NOT NULL DEFAULT 1,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id),
  UNIQUE (org_id, facility_id, claim_number),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id),
  FOREIGN KEY (org_id, encounter_id) REFERENCES encounters (org_id, id),
  FOREIGN KEY (org_id, invoice_id) REFERENCES invoices (org_id, id)
);
-- One live claim per invoice; a withdrawn one frees the invoice for a fresh assembly.
CREATE UNIQUE INDEX claims_one_live_per_invoice ON claims (org_id, invoice_id) WHERE status <> 'WITHDRAWN';
CREATE INDEX claims_facility_status ON claims (org_id, facility_id, status, created_at DESC, id DESC);
CREATE INDEX claims_patient ON claims (org_id, patient_id, created_at DESC);

CREATE TABLE claim_issues (
  id bigserial PRIMARY KEY,
  org_id uuid NOT NULL,
  claim_id uuid NOT NULL,
  rule_code text NOT NULL,
  severity text NOT NULL CHECK (severity IN ('ERROR', 'WARNING')),
  field text,
  message text NOT NULL,
  FOREIGN KEY (org_id, claim_id) REFERENCES claims (org_id, id) ON DELETE CASCADE
);
CREATE INDEX claim_issues_claim ON claim_issues (org_id, claim_id);
CREATE INDEX claim_issues_rule ON claim_issues (org_id, rule_code);

-- Every attempt to hand a claim to an adapter, whether or not anything left the building.
CREATE TABLE claim_submissions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  claim_id uuid NOT NULL,
  adapter text NOT NULL,
  verified boolean NOT NULL,
  sent boolean NOT NULL,
  outcome text NOT NULL,
  detail text,
  bundle_hash bytea NOT NULL,
  submitted_by uuid NOT NULL,
  submitted_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (org_id, claim_id) REFERENCES claims (org_id, id)
);
CREATE INDEX claim_submissions_claim ON claim_submissions (org_id, claim_id, submitted_at DESC);
CREATE TRIGGER claim_submissions_append_only BEFORE UPDATE OR DELETE ON claim_submissions FOR EACH ROW EXECUTE FUNCTION forbid_change();

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['claims', 'claim_issues', 'claim_submissions']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (org_id = current_org()) WITH CHECK (org_id = current_org())', t);
  END LOOP;
END $$;
