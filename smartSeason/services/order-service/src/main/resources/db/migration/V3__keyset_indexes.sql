-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_carts_keyset
    ON carts (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_cart_items_keyset
    ON cart_items (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_orders_keyset
    ON orders (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_order_lines_keyset
    ON order_lines (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_order_saga_states_keyset
    ON order_saga_states (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_order_returns_keyset
    ON order_returns (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_disputes_keyset
    ON disputes (tenant_id, created_at DESC, id DESC);
