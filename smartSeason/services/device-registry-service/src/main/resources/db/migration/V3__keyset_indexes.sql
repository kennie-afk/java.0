-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_devices_keyset
    ON devices (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_device_credentials_keyset
    ON device_credentials (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_firmware_releases_keyset
    ON firmware_releases (tenant_id, created_at DESC, id DESC);
