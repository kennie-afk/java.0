-- Provisioning and staff sign-in.
--
-- 1. Staff gain a human-typeable number (what a cashier keys in at the till), unique
--    within a tenant. The staff table has never had a row written by any endpoint, so the
--    backfill below only ever touches an empty table.
-- 2. A cross-tenant terminal lookup, for the same reason resolve_enrolment exists: a
--    terminal identifies itself before the server knows whose it is.
-- 3. An append-only audit log. Sign-in outcomes and provisioning actions are written here
--    and can never be edited or removed, by the application or by the owner.

ALTER TABLE staff ADD COLUMN staff_number TEXT;
UPDATE staff SET staff_number = id WHERE staff_number IS NULL;
ALTER TABLE staff ALTER COLUMN staff_number SET NOT NULL;

-- A row written without a number (the pre-provisioning code paths and their tests) gets its
-- id, which is what the backfill above does, so the column is never NULL either way.
CREATE OR REPLACE FUNCTION staff_number_default() RETURNS TRIGGER
    LANGUAGE plpgsql SET search_path = pg_catalog
AS $$
BEGIN
    IF NEW.staff_number IS NULL THEN NEW.staff_number := NEW.id; END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER staff_number_default BEFORE INSERT ON staff
    FOR EACH ROW EXECUTE FUNCTION staff_number_default();
CREATE UNIQUE INDEX staff_number_per_tenant ON staff (tenant_id, staff_number);

-- Ids minted by provisioning (STF-<16 hex>) are 20 characters, inside the shape.
ALTER TABLE staff ADD CONSTRAINT staff_number_shape CHECK (staff_number ~ '^[A-Za-z0-9-]{1,40}$');

CREATE OR REPLACE FUNCTION resolve_terminal(p_terminal_id TEXT)
    RETURNS TABLE (tenant_id TEXT, branch_id TEXT, public_key TEXT, status TEXT)
    LANGUAGE sql
    SECURITY DEFINER
    STABLE
    SET search_path = pg_catalog, public
AS $$
    SELECT t.tenant_id, t.branch_id, t.public_key, t.status
      FROM terminal t
      JOIN tenant tn ON tn.id = t.tenant_id
     WHERE t.id = p_terminal_id
       AND tn.status = 'ACTIVE'
$$;

REVOKE ALL ON FUNCTION resolve_terminal(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION resolve_terminal(TEXT) TO mara_app;

COMMENT ON FUNCTION resolve_terminal(TEXT) IS
    'Cross-tenant lookup of one terminal by the id it presents. Returns only what is needed '
    'to verify that terminal''s signature; the caller then adopts the returned tenant.';

CREATE TABLE audit_log (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    TEXT        NOT NULL REFERENCES tenant (id),
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor        TEXT        NOT NULL,      -- a staff id, 'admin', or 'terminal:<id>'
    terminal_id  TEXT,
    action       TEXT        NOT NULL,
    outcome      TEXT        NOT NULL,
    detail       TEXT
);

CREATE INDEX audit_log_by_tenant ON audit_log (tenant_id, occurred_at DESC);

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_tenant_isolation ON audit_log
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

GRANT SELECT, INSERT ON audit_log TO mara_app;
GRANT USAGE ON SEQUENCE audit_log_id_seq TO mara_app;

CREATE OR REPLACE FUNCTION audit_log_append_only() RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only' USING ERRCODE = 'insufficient_privilege';
END
$$;

CREATE TRIGGER audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_append_only();
