-- Tenant invitations: the consent step the linking flow was missing.
--
-- Linking already existed, but it was landlord-asserted: the landlord recorded an email and
-- attached whichever account had registered with it. TenantService says so in its own words --
-- "still landlord-asserted rather than tenant-accepted". Worse, registration never verified
-- email ownership, so "the account that owns this address" really meant "the account that
-- typed it", and whoever registered with a tenant's address first would receive that tenancy.
--
-- An invitation closes both. The token only reaches the inbox the landlord recorded, and it is
-- redeemed by the tenant while signed in as themselves, so the link becomes something the
-- tenant did rather than something done to them.

-- The hash, never the token. This column is as sensitive as a password reset code: anyone
-- holding the plaintext can claim the tenancy, including anyone who later reads a backup.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS invite_token_hash BYTEA;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS invite_expires_at TIMESTAMPTZ;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS invite_sent_at    TIMESTAMPTZ;

-- Redemption looks the token up by hash across every tenant row, so it needs an index; unique
-- because two live invitations sharing a hash would make "which tenancy is this?" ambiguous at
-- exactly the moment it must not be. Partial, so the many rows with no invitation do not
-- collide on NULL.
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenants_invite_token
    ON tenants (invite_token_hash) WHERE invite_token_hash IS NOT NULL;
