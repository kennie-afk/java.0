-- Outbound e-mail and SMS go through an outbox: the message is written in the same transaction as the change that caused it, and a
-- dispatcher delivers it afterwards. A failed delivery never rolls back clinical work, and a crash never loses or duplicates a message
-- that was committed.

CREATE TABLE notification_outbox (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  patient_id uuid,
  channel text NOT NULL CHECK (channel IN ('EMAIL', 'SMS')),
  recipient text NOT NULL CHECK (length(recipient) BETWEEN 3 AND 200),
  -- The template is a key; the wording lives in code. Parameters never hold a secret such as an invitation code.
  template text NOT NULL CHECK (template IN ('PORTAL_INVITED', 'PORTAL_ACTIVATED')),
  params jsonb NOT NULL DEFAULT '{}',
  status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
  attempts int NOT NULL DEFAULT 0,
  next_attempt_at timestamptz NOT NULL DEFAULT now(),
  last_error text CHECK (length(last_error) <= 500),
  created_at timestamptz NOT NULL DEFAULT now(),
  sent_at timestamptz,
  CHECK ((status = 'SENT') = (sent_at IS NOT NULL)),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id)
);
CREATE INDEX notification_outbox_due ON notification_outbox (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX notification_outbox_patient ON notification_outbox (org_id, patient_id, created_at DESC);

ALTER TABLE notification_outbox ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notification_outbox USING (org_id = current_org()) WITH CHECK (org_id = current_org());

-- The dispatcher is a background job with no organisation, so it works through two narrow owner-rights functions, like staff login.
-- Claiming leases each row (pushes its next attempt out), so two replicas never deliver the same message, and a crashed one is retried.
CREATE FUNCTION outbox_claim(p_limit int, p_lease_seconds int)
RETURNS TABLE (id uuid, org_id uuid, channel text, recipient text, template text, params jsonb, attempts int)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS
$$ WITH due AS (
     SELECT o.id FROM notification_outbox o WHERE o.status = 'PENDING' AND o.next_attempt_at <= now()
      ORDER BY o.next_attempt_at LIMIT p_limit FOR UPDATE SKIP LOCKED)
   UPDATE notification_outbox o SET attempts = o.attempts + 1, next_attempt_at = now() + make_interval(secs => p_lease_seconds)
     FROM due WHERE o.id = due.id
   RETURNING o.id, o.org_id, o.channel, o.recipient, o.template, o.params, o.attempts $$;
REVOKE ALL ON FUNCTION outbox_claim(int, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION outbox_claim(int, int) TO ${appUser};

-- A permanent failure (the provider is not configured) fails at once; a temporary one backs off and retries until the attempts run out.
CREATE FUNCTION outbox_finish(p_id uuid, p_ok boolean, p_permanent boolean, p_error text, p_max_attempts int, p_backoff_seconds int)
RETURNS void LANGUAGE sql SECURITY DEFINER SET search_path = public AS
$$ UPDATE notification_outbox SET
     status = CASE WHEN p_ok THEN 'SENT' WHEN p_permanent OR attempts >= p_max_attempts THEN 'FAILED' ELSE 'PENDING' END,
     sent_at = CASE WHEN p_ok THEN now() ELSE NULL END,
     last_error = CASE WHEN p_ok THEN NULL ELSE left(p_error, 500) END,
     next_attempt_at = CASE WHEN p_ok OR p_permanent OR attempts >= p_max_attempts THEN next_attempt_at ELSE now() + make_interval(secs => p_backoff_seconds * attempts) END
   WHERE id = p_id AND status = 'PENDING' $$;
REVOKE ALL ON FUNCTION outbox_finish(uuid, boolean, boolean, text, int, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION outbox_finish(uuid, boolean, boolean, text, int, int) TO ${appUser};

-- A portal account has just been created: tell the patient, so someone who did not do it hears about it. This fires inside
-- portal_activate, which has no tenant, so it is an owner-rights trigger. It carries no secret.
CREATE FUNCTION portal_account_created_notice() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS
$$ DECLARE pt patients%ROWTYPE; org text;
   BEGIN
     SELECT * INTO pt FROM patients WHERE org_id = NEW.org_id AND id = NEW.patient_id;
     SELECT name INTO org FROM organisations WHERE id = NEW.org_id;
     IF pt.phone IS NOT NULL AND length(trim(pt.phone)) >= 3 THEN
       INSERT INTO notification_outbox (org_id, patient_id, channel, recipient, template, params)
       VALUES (NEW.org_id, NEW.patient_id, 'SMS', trim(pt.phone), 'PORTAL_ACTIVATED', jsonb_build_object('organisation', org, 'givenName', pt.given_name));
     END IF;
     IF pt.email IS NOT NULL AND length(trim(pt.email)) >= 3 THEN
       INSERT INTO notification_outbox (org_id, patient_id, channel, recipient, template, params)
       VALUES (NEW.org_id, NEW.patient_id, 'EMAIL', trim(pt.email), 'PORTAL_ACTIVATED', jsonb_build_object('organisation', org, 'givenName', pt.given_name));
     END IF;
     RETURN NEW;
   END $$;
REVOKE ALL ON FUNCTION portal_account_created_notice() FROM PUBLIC;
CREATE TRIGGER portal_account_created AFTER INSERT ON portal_accounts FOR EACH ROW EXECUTE FUNCTION portal_account_created_notice();
