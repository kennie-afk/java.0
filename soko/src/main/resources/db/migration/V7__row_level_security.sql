-- Database-level tenant isolation.
--
-- Until now every query carried "and tenant_id = ?" in application code, and nothing at the
-- database checked it: one forgotten filter was a cross-tenant leak, and the application
-- connected as the table owner (a superuser in the compose stack), which row-level security
-- never applies to. From here on:
--
--   * the application connects as soko_app, which owns nothing, is not a superuser and cannot
--     bypass row-level security (created below, password set by afterMigrate);
--   * every tenant table has a policy: a row is visible only when its tenant_id equals
--     soko.tenant_id, which the application sets per transaction from the signed token;
--   * the few operations that genuinely span tenants (sign-in by e-mail, the M-Pesa callback,
--     the monthly billing run, the public storefront by slug) must present the system key.
--
-- The system key is not a plain flag. A flag the application can set is a flag an injected
-- query can set. soko_system() compares the presented key's hash with one stored in a table
-- soko_app cannot read, inside a SECURITY DEFINER function, so forging system access needs the
-- key itself, which lives in the application's environment and never in the database.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'soko_app') THEN
        -- NOLOGIN here: a migration must never carry a runtime credential. afterMigrate gives it
        -- a password from the environment on every start.
        CREATE ROLE soko_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    END IF;
END
$$;

CREATE TABLE soko_system_key (
    only_row  boolean PRIMARY KEY DEFAULT true CHECK (only_row),
    key_hash  bytea   NOT NULL
);
REVOKE ALL ON soko_system_key FROM PUBLIC;

CREATE FUNCTION soko_tenant() RETURNS uuid
    LANGUAGE sql STABLE
    AS $$ SELECT nullif(current_setting('soko.tenant_id', true), '')::uuid $$;

CREATE FUNCTION soko_system() RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = pg_catalog, public
    AS $$
        SELECT coalesce(current_setting('soko.system_key', true), '') <> ''
           AND EXISTS (SELECT 1 FROM soko_system_key k
                        WHERE k.key_hash = sha256(convert_to(current_setting('soko.system_key', true), 'UTF8')))
    $$;
REVOKE ALL ON FUNCTION soko_system() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION soko_tenant(), soko_system() TO soko_app;

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['users', 'suppliers', 'products', 'offers', 'customers', 'orders', 'order_lines',
                             'otp_codes', 'mpesa_payments', 'wastage_records', 'subscriptions',
                             'platform_commissions', 'invoices', 'platform_ledger']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY %I ON %I USING (tenant_id = soko_tenant() OR soko_system()) '
            'WITH CHECK (tenant_id = soko_tenant() OR soko_system())', t || '_tenant', t);
    END LOOP;
END
$$;

-- A tenant is its own row: id, not tenant_id.
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenants FORCE ROW LEVEL SECURITY;
CREATE POLICY tenants_self ON tenants
    USING (id = soko_tenant() OR soko_system()) WITH CHECK (id = soko_tenant() OR soko_system());

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO soko_app;
REVOKE ALL ON soko_system_key FROM soko_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO soko_app;
-- Tables added by later migrations are usable by the application without another grant.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO soko_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE ON SEQUENCES TO soko_app;
