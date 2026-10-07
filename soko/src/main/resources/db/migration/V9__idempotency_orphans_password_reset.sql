-- K5: a retried order placement must not place a second order. The client sends an
-- Idempotency-Key; the partial unique index makes "same key, same tenant" a hard database fact,
-- so two concurrent duplicates cannot both insert.
ALTER TABLE orders ADD COLUMN idempotency_key varchar(80);
CREATE UNIQUE INDEX uq_orders_idempotency ON orders (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- K6: a payment that succeeded at M-Pesa but whose order or invoice cannot be settled is kept
-- (status ORPHANED) with the reason, so the money is not just a log line.
ALTER TABLE mpesa_payments ADD COLUMN orphan_reason varchar(300);
CREATE INDEX idx_mpesa_payments_orphaned ON mpesa_payments (tenant_id, initiated_at DESC)
    WHERE status = 'ORPHANED';

-- K8: single-use, expiring password reset tokens. Only the SHA-256 of the token is stored.
CREATE TABLE password_resets (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT password_resets_token_unique UNIQUE (token_hash)
);
CREATE INDEX idx_password_resets_user ON password_resets (user_id) WHERE used_at IS NULL;

ALTER TABLE password_resets ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_resets FORCE ROW LEVEL SECURITY;
CREATE POLICY password_resets_tenant ON password_resets
    USING (tenant_id = soko_tenant() OR soko_system())
    WITH CHECK (tenant_id = soko_tenant() OR soko_system());
