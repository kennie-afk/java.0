-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_warehouses_keyset
    ON warehouses (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_batches_keyset
    ON batches (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_stock_items_keyset
    ON stock_items (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_grading_results_keyset
    ON grading_results (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_reservations_keyset
    ON reservations (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_input_issues_keyset
    ON input_issues (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_input_consumptions_keyset
    ON input_consumptions (tenant_id, created_at DESC, id DESC);
