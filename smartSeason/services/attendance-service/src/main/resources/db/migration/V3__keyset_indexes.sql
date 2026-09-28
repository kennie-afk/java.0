-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_geofences_keyset
    ON geofences (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_clock_events_keyset
    ON clock_events (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_shifts_keyset
    ON shifts (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_piece_rate_entries_keyset
    ON piece_rate_entries (tenant_id, created_at DESC, id DESC);
