-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_farms_keyset
    ON farms (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_plots_keyset
    ON plots (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_soil_profiles_keyset
    ON soil_profiles (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_farm_memberships_keyset
    ON farm_memberships (tenant_id, created_at DESC, id DESC);
