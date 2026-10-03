-- Scoped, expiring, individually revocable credentials for the back office and for service to
-- service calls. Replaces the two global bearer tokens (MARA_ADMIN_TOKEN / MARA_INTERNAL_TOKEN),
-- where one leak was every tenant.
--
-- The tables hold secrets' hashes and are cross-tenant by nature, so mara_app is given NO
-- privilege on them: it can only call the SECURITY DEFINER functions below, each of which does
-- one narrow thing. Row-level security is deliberately not used here: a credential is not a
-- tenant's row, and the functions are the boundary.

CREATE TABLE operator_credential (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_id       TEXT        NOT NULL UNIQUE,
    secret_hash  BYTEA       NOT NULL,
    label        TEXT        NOT NULL,
    kind         TEXT        NOT NULL,
    tenant_id    TEXT,
    scopes       TEXT[]      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   TEXT        NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,

    CONSTRAINT credential_kind_known CHECK (kind IN ('OPERATOR', 'SERVICE')),
    CONSTRAINT credential_hash_size CHECK (octet_length(secret_hash) = 32),
    CONSTRAINT credential_has_scope CHECK (cardinality(scopes) > 0),
    CONSTRAINT credential_label_nonblank CHECK (length(btrim(label)) > 0),
    -- Services act for the platform, never for one tenant.
    CONSTRAINT credential_service_untenanted CHECK (kind <> 'SERVICE' OR tenant_id IS NULL),
    CONSTRAINT credential_expiry_after_issue CHECK (expires_at > created_at)
);

CREATE INDEX operator_credential_by_label ON operator_credential (label) WHERE revoked_at IS NULL;

-- What happened to credentials: issue, rotate, revoke, and every refusal. Append-only.
CREATE TABLE operator_credential_audit (
    id            BIGSERIAL PRIMARY KEY,
    at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    credential_id UUID,
    key_id        TEXT,
    action        TEXT        NOT NULL,
    detail        TEXT,
    source_addr   TEXT,
    path          TEXT,
    CONSTRAINT credential_audit_action_known CHECK (action IN
        ('ISSUED', 'ROTATED', 'REVOKED', 'DENIED', 'USED_WRITE', 'SEEDED', 'SEED_REVOKED'))
);

CREATE INDEX operator_credential_audit_by_time ON operator_credential_audit (at DESC);

CREATE OR REPLACE FUNCTION credential_audit_append_only() RETURNS TRIGGER
    LANGUAGE plpgsql SET search_path = pg_catalog
AS $$
BEGIN
    RAISE EXCEPTION 'operator_credential_audit is append-only' USING ERRCODE = 'integrity_constraint_violation';
END
$$;

CREATE TRIGGER operator_credential_audit_append_only BEFORE UPDATE OR DELETE ON operator_credential_audit
    FOR EACH ROW EXECUTE FUNCTION credential_audit_append_only();
CREATE TRIGGER operator_credential_audit_no_truncate BEFORE TRUNCATE ON operator_credential_audit
    FOR EACH STATEMENT EXECUTE FUNCTION credential_audit_append_only();

REVOKE ALL ON operator_credential, operator_credential_audit FROM PUBLIC;
REVOKE ALL ON operator_credential, operator_credential_audit FROM mara_app;

-- ------------------------------------------------------------------ functions

CREATE OR REPLACE FUNCTION credential_lookup(p_key_id TEXT)
    RETURNS TABLE (id UUID, secret_hash BYTEA, label TEXT, kind TEXT, tenant_id TEXT, scopes TEXT[],
                   expires_at TIMESTAMPTZ, revoked_at TIMESTAMPTZ)
    LANGUAGE sql SECURITY DEFINER STABLE SET search_path = pg_catalog, public
AS $$
    SELECT c.id, c.secret_hash, c.label, c.kind, c.tenant_id, c.scopes, c.expires_at, c.revoked_at
      FROM operator_credential c WHERE c.key_id = p_key_id
$$;

-- Stamps last use at most once a minute, so verifying a credential on every request does not
-- turn into a write on every request.
CREATE OR REPLACE FUNCTION credential_touch(p_id UUID) RETURNS VOID
    LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, public
AS $$
    UPDATE operator_credential SET last_used_at = now()
     WHERE id = p_id AND (last_used_at IS NULL OR last_used_at < now() - interval '1 minute')
$$;

CREATE OR REPLACE FUNCTION credential_issue(
        p_key_id TEXT, p_secret_hash BYTEA, p_label TEXT, p_kind TEXT, p_tenant TEXT, p_scopes TEXT[],
        p_created_by TEXT, p_expires_at TIMESTAMPTZ)
    RETURNS UUID
    LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, public
AS $$
    INSERT INTO operator_credential (key_id, secret_hash, label, kind, tenant_id, scopes, created_by, expires_at)
    VALUES (p_key_id, p_secret_hash, p_label, p_kind, p_tenant, p_scopes, p_created_by, p_expires_at)
    RETURNING id
$$;

CREATE OR REPLACE FUNCTION credential_revoke(p_id UUID) RETURNS BOOLEAN
    LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, public
AS $$
    WITH r AS (UPDATE operator_credential SET revoked_at = now() WHERE id = p_id AND revoked_at IS NULL RETURNING 1)
    SELECT EXISTS (SELECT 1 FROM r)
$$;

-- Shorten a credential's life (rotation grace): never extends it.
CREATE OR REPLACE FUNCTION credential_expire_by(p_id UUID, p_at TIMESTAMPTZ) RETURNS VOID
    LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, public
AS $$
    UPDATE operator_credential SET expires_at = least(expires_at, greatest(p_at, created_at + interval '1 microsecond'))
     WHERE id = p_id AND revoked_at IS NULL
$$;

CREATE OR REPLACE FUNCTION credential_list(p_limit INTEGER)
    RETURNS TABLE (id UUID, key_id TEXT, label TEXT, kind TEXT, tenant_id TEXT, scopes TEXT[], created_at TIMESTAMPTZ,
                   created_by TEXT, expires_at TIMESTAMPTZ, revoked_at TIMESTAMPTZ, last_used_at TIMESTAMPTZ)
    LANGUAGE sql SECURITY DEFINER STABLE SET search_path = pg_catalog, public
AS $$
    SELECT c.id, c.key_id, c.label, c.kind, c.tenant_id, c.scopes, c.created_at, c.created_by, c.expires_at,
           c.revoked_at, c.last_used_at
      FROM operator_credential c ORDER BY c.created_at DESC LIMIT least(greatest(p_limit, 1), 500)
$$;

CREATE OR REPLACE FUNCTION credential_audit_write(
        p_credential UUID, p_key_id TEXT, p_action TEXT, p_detail TEXT, p_addr TEXT, p_path TEXT) RETURNS VOID
    LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, public
AS $$
    INSERT INTO operator_credential_audit (credential_id, key_id, action, detail, source_addr, path)
    VALUES (p_credential, p_key_id, p_action, left(p_detail, 300), left(p_addr, 64), left(p_path, 200))
$$;

CREATE OR REPLACE FUNCTION credential_audit_read(p_limit INTEGER)
    RETURNS TABLE (id BIGINT, at TIMESTAMPTZ, credential_id UUID, key_id TEXT, action TEXT, detail TEXT,
                   source_addr TEXT, path TEXT)
    LANGUAGE sql SECURITY DEFINER STABLE SET search_path = pg_catalog, public
AS $$
    SELECT a.id, a.at, a.credential_id, a.key_id, a.action, a.detail, a.source_addr, a.path
      FROM operator_credential_audit a ORDER BY a.id DESC LIMIT least(greatest(p_limit, 1), 500)
$$;

-- Startup seeding from the environment: a credential the deployer minted, registered by key id
-- with fixed scopes. Idempotent. A credential someone revoked stays revoked (a restart must not
-- resurrect it). Every other live credential with the same label that is not in p_keep is
-- revoked, which is how a rotated-out credential stops working once its overlap is removed.
CREATE OR REPLACE FUNCTION credential_seed(
        p_label TEXT, p_kind TEXT, p_scopes TEXT[], p_key_id TEXT, p_secret_hash BYTEA, p_keep TEXT[],
        p_expires_at TIMESTAMPTZ)
    RETURNS INTEGER
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public
AS $$
DECLARE
    v_revoked INTEGER;
BEGIN
    IF p_key_id IS NOT NULL THEN
        INSERT INTO operator_credential (key_id, secret_hash, label, kind, tenant_id, scopes, created_by, expires_at)
        VALUES (p_key_id, p_secret_hash, p_label, p_kind, NULL, p_scopes, 'seed', p_expires_at)
        ON CONFLICT (key_id) DO UPDATE
           SET expires_at = greatest(operator_credential.expires_at, EXCLUDED.expires_at),
               scopes = EXCLUDED.scopes
         WHERE operator_credential.revoked_at IS NULL AND operator_credential.secret_hash = EXCLUDED.secret_hash;
    END IF;
    WITH r AS (
        UPDATE operator_credential SET revoked_at = now()
         WHERE label = p_label AND kind = p_kind AND revoked_at IS NULL AND created_by = 'seed'
           AND NOT (key_id = ANY (coalesce(p_keep, ARRAY[]::TEXT[])))
        RETURNING 1)
    SELECT count(*) INTO v_revoked FROM r;
    RETURN v_revoked;
END
$$;

DO $$
DECLARE
    f TEXT;
BEGIN
    FOREACH f IN ARRAY ARRAY[
        'credential_lookup(text)', 'credential_touch(uuid)',
        'credential_issue(text, bytea, text, text, text, text[], text, timestamptz)', 'credential_revoke(uuid)',
        'credential_expire_by(uuid, timestamptz)', 'credential_list(integer)',
        'credential_audit_write(uuid, text, text, text, text, text)', 'credential_audit_read(integer)',
        'credential_seed(text, text, text[], text, bytea, text[], timestamptz)']
    LOOP
        EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC', f);
        EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO mara_app', f);
    END LOOP;
END
$$;
