-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_supply_listings_keyset
    ON supply_listings (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_demand_posts_keyset
    ON demand_posts (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_offers_keyset
    ON offers (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_market_matches_keyset
    ON market_matches (tenant_id, created_at DESC, id DESC);
