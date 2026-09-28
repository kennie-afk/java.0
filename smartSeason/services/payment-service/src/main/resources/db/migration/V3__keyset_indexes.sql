-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_payment_intents_keyset
    ON payment_intents (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_mpesa_transactions_keyset
    ON mpesa_transactions (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_escrow_holds_keyset
    ON escrow_holds (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_wallets_keyset
    ON wallets (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_provider_callbacks_keyset
    ON provider_callbacks (tenant_id, created_at DESC, id DESC);
