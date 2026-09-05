CREATE TABLE settlements (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    settlement_number      VARCHAR(255) NOT NULL UNIQUE,
    payee_org_id           UUID,
    payee_user_id          UUID,
    order_id               UUID,
    gross_amount           NUMERIC(18,4) NOT NULL,
    commission             NUMERIC(18,4) NOT NULL,
    fees                   NUMERIC(18,4) NOT NULL,
    net_amount             NUMERIC(18,4) NOT NULL,
    currency               VARCHAR(255) NOT NULL,
    period_start           DATE,
    period_end             DATE,
    due_at                 TIMESTAMPTZ,
    status                 VARCHAR(64) NOT NULL,
    approved_by            UUID
);
CREATE INDEX ix_settlements_tenant ON settlements (tenant_id);
CREATE INDEX ix_settlements_payee_org_id ON settlements (payee_org_id);
CREATE INDEX ix_settlements_payee_user_id ON settlements (payee_user_id);
CREATE INDEX ix_settlements_order_id ON settlements (order_id);

CREATE TABLE payout_batches (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    batch_number           VARCHAR(255) NOT NULL UNIQUE,
    farm_id                UUID,
    payout_type            VARCHAR(64) NOT NULL,
    item_count             INTEGER NOT NULL,
    total_amount           NUMERIC(18,4) NOT NULL,
    currency               VARCHAR(255) NOT NULL,
    scheduled_for          TIMESTAMPTZ,
    submitted_at           TIMESTAMPTZ,
    completed_at           TIMESTAMPTZ,
    created_by             UUID,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_payout_batches_tenant ON payout_batches (tenant_id);
CREATE INDEX ix_payout_batches_farm_id ON payout_batches (farm_id);

CREATE TABLE payout_items (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    batch_id               UUID,
    settlement_id          UUID,
    payee_type             VARCHAR(64) NOT NULL,
    payee_id               UUID NOT NULL,
    payee_name             VARCHAR(255),
    payee_phone            VARCHAR(255),
    amount                 NUMERIC(18,4) NOT NULL,
    currency               VARCHAR(255) NOT NULL,
    payment_intent_id      UUID,
    status                 VARCHAR(64) NOT NULL,
    failure_reason         VARCHAR(255),
    sent_at                TIMESTAMPTZ,
    paid_at                TIMESTAMPTZ,
    idempotency_key        VARCHAR(255) UNIQUE
);
CREATE INDEX ix_payout_items_tenant ON payout_items (tenant_id);
CREATE INDEX ix_payout_items_batch_id ON payout_items (batch_id);
CREATE INDEX ix_payout_items_settlement_id ON payout_items (settlement_id);
CREATE INDEX ix_payout_items_payee_id ON payout_items (payee_id);
CREATE INDEX ix_payout_items_payment_intent_id ON payout_items (payment_intent_id);

CREATE TABLE payout_holds (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    payout_item_id         UUID,
    payee_id               UUID NOT NULL,
    reason                 VARCHAR(64) NOT NULL,
    fraud_case_id          UUID,
    amount                 NUMERIC(18,4),
    held_at                TIMESTAMPTZ NOT NULL,
    held_by                UUID,
    released_at            TIMESTAMPTZ,
    released_by            UUID,
    status                 VARCHAR(64) NOT NULL,
    notes                  TEXT
);
CREATE INDEX ix_payout_holds_tenant ON payout_holds (tenant_id);
CREATE INDEX ix_payout_holds_payout_item_id ON payout_holds (payout_item_id);
CREATE INDEX ix_payout_holds_payee_id ON payout_holds (payee_id);
CREATE INDEX ix_payout_holds_fraud_case_id ON payout_holds (fraud_case_id);
