-- Remove the agent role.
--
-- AGENT existed as a badge that conferred nothing: an application, an admin review and a
-- licence document, after which the role granted exactly what SELLER already granted.
-- A role that implies a check to buyers without performing one is worse than no role, so
-- it goes.
--
-- Anyone currently holding it becomes a SELLER. That is the honest mapping: what an
-- AGENT could actually do on this platform was list properties, which is what a SELLER
-- does. Nobody loses access and no listing is orphaned.

-- Order matters. The constraint has to go first, or the UPDATE below is checked against
-- a rule that still permits the value we are removing while the new one is not yet in
-- place.
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;

UPDATE users SET role = 'SELLER' WHERE role = 'AGENT';

ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('BUYER', 'SELLER', 'LANDLORD', 'ADMIN'));

-- The applications themselves are dropped rather than archived. They hold a business
-- name and a document URL for a process that no longer exists, and keeping personal data
-- for a discontinued feature is a retention problem, not a courtesy.
DROP TABLE IF EXISTS agent_applications;
