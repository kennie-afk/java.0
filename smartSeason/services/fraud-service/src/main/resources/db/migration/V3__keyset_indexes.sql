-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_fraud_rules_keyset
    ON fraud_rules (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_fraud_signals_keyset
    ON fraud_signals (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_fraud_cases_keyset
    ON fraud_cases (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_fraud_evidence_keyset
    ON fraud_evidence (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_worker_risk_scores_keyset
    ON worker_risk_scores (tenant_id, created_at DESC, id DESC);
