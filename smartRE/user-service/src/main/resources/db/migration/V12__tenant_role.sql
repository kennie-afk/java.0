-- TENANT joins the role set.
--
-- A renting tenant was previously a BUYER whose relationship happened to be a tenancy. That
-- worked as a label and failed as authorisation: the /my-tenancy endpoints were reachable by
-- any authenticated account, and a tenant carried permissions for a sale marketplace they
-- have no use for.
--
-- Forward-only, replacing the constraint V11 left behind. Existing tenants are NOT migrated
-- automatically and cannot be: whether a BUYER is really a tenant is recorded in
-- property-management-service's own database (tenants.user_id), which this service cannot
-- read. Promote them deliberately, e.g.
--   UPDATE users SET role = 'TENANT' WHERE id IN (<ids with a tenants.user_id link>);
-- Leaving them as BUYER keeps working; they simply do not gain the tenant-only routes.
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('BUYER', 'SELLER', 'LANDLORD', 'TENANT', 'ADMIN'));
