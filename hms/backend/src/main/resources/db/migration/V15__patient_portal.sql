-- Patient portal: patient-owned accounts (separate from staff), invitation codes issued in person, appointment requests,
-- and the release flag that decides which results a patient may see.

CREATE TABLE portal_invitations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  -- Only a hash is kept: the code is shown once to the staff member, who hands it to the patient.
  code_hash bytea NOT NULL UNIQUE,
  created_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  revoked_at timestamptz,
  attempts int NOT NULL DEFAULT 0,
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id)
);
CREATE INDEX portal_invitations_patient ON portal_invitations (org_id, patient_id);

CREATE TABLE portal_accounts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  login citext NOT NULL CHECK (length(login) BETWEEN 5 AND 120),
  password_hash text NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
  failed_logins int NOT NULL DEFAULT 0,
  locked_until timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_login_at timestamptz,
  UNIQUE (org_id, login),
  UNIQUE (org_id, patient_id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id)
);

CREATE TABLE appointment_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  preferred_date date NOT NULL,
  reason text NOT NULL CHECK (length(reason) BETWEEN 3 AND 500),
  status text NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED', 'SCHEDULED', 'DECLINED', 'CANCELLED')),
  response_note text CHECK (length(response_note) <= 500),
  resolved_by uuid,
  resolved_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id),
  CHECK (status = 'CANCELLED' OR ((status IN ('SCHEDULED', 'DECLINED')) = (resolved_by IS NOT NULL))),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id)
);
CREATE INDEX appointment_requests_open ON appointment_requests (org_id, facility_id, status, created_at);
CREATE INDEX appointment_requests_patient ON appointment_requests (org_id, patient_id, created_at DESC);

-- A result reaches the patient only after a clinician releases it; changing a released result withdraws the release.
ALTER TABLE lab_order_items ADD COLUMN released_at timestamptz, ADD COLUMN released_by uuid,
  ADD CONSTRAINT lab_release_validated CHECK (released_at IS NULL OR status = 'VALIDATED');
ALTER TABLE imaging_orders ADD COLUMN released_at timestamptz, ADD COLUMN released_by uuid,
  ADD CONSTRAINT imaging_release_signed CHECK (released_at IS NULL OR status = 'SIGNED');

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['portal_invitations', 'portal_accounts', 'appointment_requests']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (org_id = current_org()) WITH CHECK (org_id = current_org())', t);
  END LOOP;
END $$;

-- Sign-in and activation happen before any tenant exists, so they go through narrow owner-rights functions, like staff login.
CREATE FUNCTION portal_login_lookup(p_slug citext, p_login citext)
RETURNS TABLE (id uuid, org_id uuid, patient_id uuid, password_hash text, status text, failed_logins int, locked_until timestamptz, org_status text)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS
$$ SELECT a.id, a.org_id, a.patient_id, a.password_hash, a.status, a.failed_logins, a.locked_until, o.status
     FROM portal_accounts a JOIN organisations o ON o.id = a.org_id WHERE o.slug = p_slug AND a.login = p_login $$;
REVOKE ALL ON FUNCTION portal_login_lookup(citext, citext) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION portal_login_lookup(citext, citext) TO ${appUser};

CREATE FUNCTION portal_record_login_result(p_id uuid, p_success boolean, p_lock_after int, p_lock_minutes int)
RETURNS void LANGUAGE sql SECURITY DEFINER SET search_path = public AS
$$ UPDATE portal_accounts SET
     failed_logins = CASE WHEN p_success THEN 0 ELSE failed_logins + 1 END,
     locked_until = CASE WHEN p_success THEN NULL
                         WHEN failed_logins + 1 >= p_lock_after THEN now() + make_interval(mins => p_lock_minutes)
                         ELSE locked_until END,
     last_login_at = CASE WHEN p_success THEN now() ELSE last_login_at END
   WHERE id = p_id $$;
REVOKE ALL ON FUNCTION portal_record_login_result(uuid, boolean, int, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION portal_record_login_result(uuid, boolean, int, int) TO ${appUser};

-- Redeems an invitation. The patient must also know their date of birth; five wrong dates burn the invitation. Every
-- failure answers 'invalid' so the reply does not say whether a code exists.
CREATE FUNCTION portal_activate(p_slug citext, p_code_hash bytea, p_birth_date date, p_login citext, p_password_hash text)
RETURNS text LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS
$$ DECLARE inv portal_invitations%ROWTYPE; born date;
   BEGIN
     SELECT i.* INTO inv FROM portal_invitations i JOIN organisations o ON o.id = i.org_id
      WHERE o.slug = p_slug AND i.code_hash = p_code_hash FOR UPDATE OF i;
     IF NOT FOUND OR inv.used_at IS NOT NULL OR inv.revoked_at IS NOT NULL OR inv.expires_at < now() OR inv.attempts >= 5 THEN
       RETURN 'invalid';
     END IF;
     SELECT birth_date INTO born FROM patients WHERE org_id = inv.org_id AND id = inv.patient_id;
     IF born IS DISTINCT FROM p_birth_date THEN
       UPDATE portal_invitations SET attempts = attempts + 1 WHERE id = inv.id;
       RETURN 'invalid';
     END IF;
     IF EXISTS (SELECT 1 FROM portal_accounts WHERE org_id = inv.org_id AND patient_id = inv.patient_id) THEN
       RETURN 'account_exists';
     END IF;
     IF EXISTS (SELECT 1 FROM portal_accounts WHERE org_id = inv.org_id AND login = p_login) THEN
       RETURN 'login_taken';
     END IF;
     INSERT INTO portal_accounts (org_id, patient_id, login, password_hash) VALUES (inv.org_id, inv.patient_id, p_login, p_password_hash);
     UPDATE portal_invitations SET used_at = now() WHERE id = inv.id;
     RETURN 'ok';
   END $$;
REVOKE ALL ON FUNCTION portal_activate(citext, bytea, date, citext, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION portal_activate(citext, bytea, date, citext, text) TO ${appUser};

UPDATE roles SET permissions = permissions || '["portal:manage"]'::jsonb WHERE is_system AND role_key = 'RECORDS_OFFICER' AND NOT permissions ? 'portal:manage';
UPDATE roles SET permissions = permissions || '["portal:release"]'::jsonb WHERE is_system AND role_key IN ('DOCTOR', 'CLINICAL_OFFICER') AND NOT permissions ? 'portal:release';
