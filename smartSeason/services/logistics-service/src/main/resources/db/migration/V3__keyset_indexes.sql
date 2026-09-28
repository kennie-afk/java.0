-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_vehicles_keyset
    ON vehicles (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_drivers_keyset
    ON drivers (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_transport_jobs_keyset
    ON transport_jobs (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_route_stops_keyset
    ON route_stops (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_proofs_of_delivery_keyset
    ON proofs_of_delivery (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_cold_chain_readings_keyset
    ON cold_chain_readings (tenant_id, created_at DESC, id DESC);
