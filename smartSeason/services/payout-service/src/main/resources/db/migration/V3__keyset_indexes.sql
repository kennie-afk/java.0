-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_settlements_keyset
    ON settlements (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_payout_batches_keyset
    ON payout_batches (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_payout_items_keyset
    ON payout_items (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_payout_holds_keyset
    ON payout_holds (tenant_id, created_at DESC, id DESC);
