-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_work_orders_keyset
    ON work_orders (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_task_assignments_keyset
    ON task_assignments (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_task_evidence_keyset
    ON task_evidence (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_checklist_items_keyset
    ON checklist_items (tenant_id, created_at DESC, id DESC);
