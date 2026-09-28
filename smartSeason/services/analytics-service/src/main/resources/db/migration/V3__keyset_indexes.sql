-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_metric_snapshots_keyset
    ON metric_snapshots (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_reports_keyset
    ON reports (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_report_runs_keyset
    ON report_runs (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_dashboard_widgets_keyset
    ON dashboard_widgets (tenant_id, created_at DESC, id DESC);
