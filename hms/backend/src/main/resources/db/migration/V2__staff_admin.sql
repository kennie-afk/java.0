-- Staff administration: password lifecycle, facility contact details, and the new permissions
-- (scheduling, inpatient) granted to the matching standard roles of organisations that already exist.
ALTER TABLE practitioners
  ADD COLUMN must_change_password boolean NOT NULL DEFAULT false,
  ADD COLUMN password_changed_at timestamptz,
  ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE facilities
  ADD COLUMN phone text,
  ADD COLUMN address_line text;

UPDATE roles SET permissions = permissions || '["scheduling:read","scheduling:write","inpatient:read","inpatient:write"]'::jsonb
 WHERE is_system AND role_key IN ('DOCTOR', 'NURSE')
   AND NOT permissions ? 'scheduling:read';
UPDATE roles SET permissions = permissions || '["scheduling:read","scheduling:write","inpatient:read"]'::jsonb
 WHERE is_system AND role_key = 'CLINICAL_OFFICER' AND NOT permissions ? 'scheduling:read';
UPDATE roles SET permissions = permissions || '["scheduling:read","scheduling:write"]'::jsonb
 WHERE is_system AND role_key = 'RECORDS_OFFICER' AND NOT permissions ? 'scheduling:read';
