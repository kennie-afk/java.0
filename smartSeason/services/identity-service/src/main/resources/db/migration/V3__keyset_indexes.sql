-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_organisations_keyset
    ON organisations (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_users_keyset
    ON users (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_keyset
    ON refresh_tokens (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_kyc_records_keyset
    ON kyc_records (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_otp_challenges_keyset
    ON otp_challenges (tenant_id, created_at DESC, id DESC);
