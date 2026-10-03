-- Tenant isolation: row-level security on every table that carries tenant_id.
--
-- Flyway runs this callback as the schema OWNER after every migrate, and it is idempotent:
-- a table that is already protected is left alone, so a restart takes no locks. Because it
-- discovers tables from the catalogue instead of listing them, a table added by any future
-- migration is protected the next time the service starts - there is no list to forget to
-- update. The service refuses to boot (RlsGuard) if one is still unprotected.
--
-- The policy is deliberately strict and index-friendly: a row is visible and writable only
-- when its tenant_id equals the tenant bound to the current transaction. There is no
-- "or system" escape hatch in the policy - an OR would stop Postgres using the tenant index.
-- The few paths that legitimately cross tenants use narrow SECURITY DEFINER functions that
-- return a tenant id and nothing else.
--
-- outbox_events is excluded on purpose: the relay publishes pending events for every tenant,
-- the table is never exposed through any endpoint, and each event already carries its tenant.

CREATE OR REPLACE FUNCTION app_tenant_id() RETURNS uuid
    LANGUAGE sql STABLE PARALLEL SAFE
    AS $fn$ SELECT nullif(current_setting('app.tenant_id', true), '')::uuid $fn$;

DO $rls$
DECLARE
    app_role text := '${app_role}';
    t record;
BEGIN
    FOR t IN
        SELECT c.oid, c.relname, c.relrowsecurity
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind IN ('r', 'p')
          AND NOT c.relispartition
          AND c.relname <> 'outbox_events'
          AND EXISTS (SELECT 1 FROM pg_attribute a
                      WHERE a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped)
    LOOP
        IF NOT t.relrowsecurity THEN
            EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t.relname);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_policies p
                       WHERE p.schemaname = 'public' AND p.tablename = t.relname
                         AND p.policyname = 'tenant_isolation') THEN
            EXECUTE format(
                'CREATE POLICY tenant_isolation ON %I USING (tenant_id = app_tenant_id()) '
                'WITH CHECK (tenant_id = app_tenant_id())', t.relname);
        END IF;
    END LOOP;

    -- Privileges for the application role. Done here, not with ALTER DEFAULT PRIVILEGES, so a
    -- table or sequence created by any later migration is covered without a second step.
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = app_role) THEN
        EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', app_role);
        FOR t IN
            SELECT c.oid, c.relname, c.relkind
            FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'S')
              AND c.relname <> 'flyway_schema_history'
        LOOP
            IF t.relkind = 'S' THEN
                IF NOT has_sequence_privilege(app_role, t.oid, 'USAGE') THEN
                    EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE %I TO %I', t.relname, app_role);
                END IF;
            ELSIF NOT has_table_privilege(app_role, t.oid, 'SELECT')
                  OR NOT has_table_privilege(app_role, t.oid, 'DELETE') THEN
                EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON %I TO %I', t.relname, app_role);
            END IF;
        END LOOP;
        EXECUTE format('GRANT EXECUTE ON FUNCTION app_tenant_id() TO %I', app_role);
        FOR t IN
            SELECT p.oid::regprocedure AS sig
            FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname = 'public' AND p.proname LIKE 'ss\_%'
        LOOP
            EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO %I', t.sig, app_role);
        END LOOP;
    END IF;
END
$rls$;
