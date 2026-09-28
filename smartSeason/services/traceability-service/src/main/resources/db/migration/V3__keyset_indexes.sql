-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_trace_batches_keyset
    ON trace_batches (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_trace_links_keyset
    ON trace_links (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_qr_passes_keyset
    ON qr_passes (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_cert_evidence_keyset
    ON cert_evidence (tenant_id, created_at DESC, id DESC);
