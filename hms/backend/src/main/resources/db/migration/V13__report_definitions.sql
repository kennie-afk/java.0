-- Configurable aggregate reports. A definition is a named list of data elements; each element picks a measure from the
-- built-in catalogue (fixed, parameterised queries in code: a definition never contains SQL) plus a disaggregation and filter.
-- These are the organisation's own operational reports, not the Ministry of Health's official forms.

CREATE TABLE report_definitions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL REFERENCES organisations (id),
  code text NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9_.-]{0,39}$'),
  name text NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
  description text CHECK (length(description) <= 1000),
  elements jsonb NOT NULL CHECK (jsonb_typeof(elements) = 'array' AND jsonb_array_length(elements) BETWEEN 1 AND 60),
  -- Optional DHIS2 export mapping: {"dataSet": "<uid>", "orgUnits": {"<facility id>": "<uid>"}}. Identifiers come from the organisation's own DHIS2.
  dhis2 jsonb,
  active boolean NOT NULL DEFAULT true,
  created_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  version int NOT NULL DEFAULT 1,
  UNIQUE (org_id, id),
  UNIQUE (org_id, code)
);

ALTER TABLE report_definitions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON report_definitions USING (org_id = current_org()) WITH CHECK (org_id = current_org());
