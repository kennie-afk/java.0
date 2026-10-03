-- Cross-tenant lookups that row-level security would otherwise make impossible.
--
-- Sign-in is by e-mail and refresh is by token: there is no tenant yet when the first query
-- runs, and an unbound query sees no rows. These two functions run as their owner (the schema
-- owner, which row-level security does not apply to) and return ONLY a tenant id - never a
-- row, a name or a hash - so the application can bind that tenant and carry on with ordinary
-- filtered queries. They are the only door through the policy, which is why they are few and
-- named ss_* (the tenant-isolation callback grants EXECUTE on exactly that prefix).

CREATE OR REPLACE FUNCTION ss_identity_tenant_by_email(p_email text) RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
    AS $$ SELECT tenant_id FROM users WHERE email = p_email LIMIT 1 $$;

CREATE OR REPLACE FUNCTION ss_identity_tenant_by_refresh_hash(p_hash text) RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
    AS $$ SELECT tenant_id FROM refresh_tokens WHERE token_hash = p_hash LIMIT 1 $$;

REVOKE ALL ON FUNCTION ss_identity_tenant_by_email(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION ss_identity_tenant_by_refresh_hash(text) FROM PUBLIC;
