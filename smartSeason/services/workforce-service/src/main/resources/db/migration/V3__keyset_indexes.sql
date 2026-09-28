-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_workers_keyset
    ON workers (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_worker_contracts_keyset
    ON worker_contracts (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_wage_rates_keyset
    ON wage_rates (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_gangs_keyset
    ON gangs (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_gang_memberships_keyset
    ON gang_memberships (tenant_id, created_at DESC, id DESC);
