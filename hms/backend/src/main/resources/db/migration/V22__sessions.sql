-- Staff sessions: short access tokens plus rotating refresh tokens, and a way to end every session of one person at once.
--
-- tokens_valid_after: an access token carries the moment it was issued (database clock, milliseconds); once this column is later than that,
-- the token is dead, whatever its signature says. Ending sessions (password change or reset, "sign out everywhere") sets it to now.
ALTER TABLE practitioners ADD COLUMN tokens_valid_after timestamptz NOT NULL DEFAULT 'epoch';

-- Refresh tokens are opaque random strings; only their SHA-256 is stored, so a leaked table cannot be replayed. Each is used once: using it
-- issues the next one in the same family. Presenting an already-used one later than the grace period means two parties hold the same token,
-- so the whole family is revoked. Nothing here is reachable by the API role directly: the functions below run with owner rights, like login.
CREATE TABLE refresh_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  family_id uuid NOT NULL,
  org_id uuid NOT NULL REFERENCES organisations (id),
  practitioner_id uuid NOT NULL REFERENCES practitioners (id),
  token_hash bytea NOT NULL UNIQUE,
  issued_at timestamptz NOT NULL DEFAULT now(),
  family_started_at timestamptz NOT NULL,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  revoked_at timestamptz
);
CREATE INDEX refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_practitioner ON refresh_tokens (practitioner_id);
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON refresh_tokens FROM ${appUser};

CREATE FUNCTION db_clock_millis() RETURNS bigint LANGUAGE sql STABLE AS $$ SELECT floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint $$;
GRANT EXECUTE ON FUNCTION db_clock_millis() TO ${appUser};

CREATE FUNCTION refresh_issue(p_org uuid, p_practitioner uuid, p_hash bytea, p_ttl_seconds int)
RETURNS void LANGUAGE sql SECURITY DEFINER SET search_path = public AS
$$ INSERT INTO refresh_tokens (family_id, org_id, practitioner_id, token_hash, family_started_at, expires_at)
   SELECT gen_random_uuid(), p.org_id, p.id, p_hash, now(), now() + make_interval(secs => p_ttl_seconds)
     FROM practitioners p WHERE p.id = p_practitioner AND p.org_id = p_org $$;
REVOKE ALL ON FUNCTION refresh_issue(uuid, uuid, bytea, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION refresh_issue(uuid, uuid, bytea, int) TO ${appUser};

-- OK: used, the next token was stored. GRACE: used a moment ago (two tabs refreshing together), family intact: the caller gets a new access
-- token but no new refresh token. REUSED: used long ago, the family is now revoked. INVALID: unknown, expired, revoked, or the person is no longer active.
CREATE FUNCTION refresh_rotate(p_hash bytea, p_new_hash bytea, p_ttl_seconds int, p_family_max_seconds int, p_grace_seconds int)
RETURNS TABLE (outcome text, org_id uuid, practitioner_id uuid)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS
$$ DECLARE r refresh_tokens%ROWTYPE; active boolean;
   BEGIN
     SELECT * INTO r FROM refresh_tokens t WHERE t.token_hash = p_hash FOR UPDATE;
     IF NOT FOUND OR r.revoked_at IS NOT NULL OR r.expires_at <= now() THEN
       RETURN QUERY SELECT 'INVALID'::text, NULL::uuid, NULL::uuid; RETURN;
     END IF;
     SELECT (pr.status = 'ACTIVE' AND o.status = 'ACTIVE') INTO active FROM practitioners pr JOIN organisations o ON o.id = pr.org_id WHERE pr.id = r.practitioner_id;
     IF NOT coalesce(active, false) THEN
       UPDATE refresh_tokens t SET revoked_at = now() WHERE t.family_id = r.family_id AND t.revoked_at IS NULL;
       RETURN QUERY SELECT 'INVALID'::text, NULL::uuid, NULL::uuid; RETURN;
     END IF;
     IF r.used_at IS NOT NULL THEN
       IF r.used_at > now() - make_interval(secs => p_grace_seconds) THEN
         RETURN QUERY SELECT 'GRACE'::text, r.org_id, r.practitioner_id; RETURN;
       END IF;
       UPDATE refresh_tokens t SET revoked_at = now() WHERE t.family_id = r.family_id AND t.revoked_at IS NULL;
       RETURN QUERY SELECT 'REUSED'::text, r.org_id, r.practitioner_id; RETURN;
     END IF;
     IF r.family_started_at + make_interval(secs => p_family_max_seconds) <= now() THEN
       RETURN QUERY SELECT 'INVALID'::text, NULL::uuid, NULL::uuid; RETURN;
     END IF;
     UPDATE refresh_tokens t SET used_at = now() WHERE t.id = r.id;
     INSERT INTO refresh_tokens (family_id, org_id, practitioner_id, token_hash, family_started_at, expires_at)
       VALUES (r.family_id, r.org_id, r.practitioner_id, p_new_hash, r.family_started_at, least(now() + make_interval(secs => p_ttl_seconds), r.family_started_at + make_interval(secs => p_family_max_seconds)));
     RETURN QUERY SELECT 'OK'::text, r.org_id, r.practitioner_id;
   END $$;
REVOKE ALL ON FUNCTION refresh_rotate(bytea, bytea, int, int, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION refresh_rotate(bytea, bytea, int, int, int) TO ${appUser};

-- Sign out one device: revoke the family the presented token belongs to. Unknown tokens are ignored (the answer must not reveal which exist).
CREATE FUNCTION refresh_revoke_family(p_hash bytea) RETURNS void LANGUAGE sql SECURITY DEFINER SET search_path = public AS
$$ UPDATE refresh_tokens t SET revoked_at = now() WHERE t.revoked_at IS NULL
      AND t.family_id = (SELECT f.family_id FROM refresh_tokens f WHERE f.token_hash = p_hash) $$;
REVOKE ALL ON FUNCTION refresh_revoke_family(bytea) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION refresh_revoke_family(bytea) TO ${appUser};

-- End every session of one person: kills the access tokens already issued and every refresh token.
CREATE FUNCTION sessions_revoke_all(p_org uuid, p_practitioner uuid) RETURNS void LANGUAGE sql SECURITY DEFINER SET search_path = public AS
$$ WITH r AS (UPDATE refresh_tokens t SET revoked_at = now() WHERE t.practitioner_id = p_practitioner AND t.org_id = p_org AND t.revoked_at IS NULL)
   UPDATE practitioners SET tokens_valid_after = clock_timestamp() WHERE id = p_practitioner AND org_id = p_org $$;
REVOKE ALL ON FUNCTION sessions_revoke_all(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION sessions_revoke_all(uuid, uuid) TO ${appUser};
