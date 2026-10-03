-- A built-in role for an integration account that reads through the FHIR facade. Existing organisations get it too.
INSERT INTO roles (org_id, role_key, label, description, is_system, permissions)
SELECT id, 'FHIR_CLIENT', 'Integration (FHIR read)', 'Reads patient records through the FHIR interface; cannot change anything', true,
       '["fhir:read","patients:read","clinical:read","lab:read"]'::jsonb
  FROM organisations ON CONFLICT (org_id, role_key) DO NOTHING;
