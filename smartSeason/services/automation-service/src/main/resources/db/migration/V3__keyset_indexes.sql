-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_automation_rules_keyset
    ON automation_rules (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_actuator_commands_keyset
    ON actuator_commands (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_digital_twins_keyset
    ON digital_twins (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_safety_interlocks_keyset
    ON safety_interlocks (tenant_id, created_at DESC, id DESC);
