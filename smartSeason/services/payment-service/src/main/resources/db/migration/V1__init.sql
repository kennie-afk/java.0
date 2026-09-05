CREATE TABLE outbox_events (
    id            UUID PRIMARY KEY,
    tenant_id     UUID,
    topic         VARCHAR(255) NOT NULL,
    message_key   VARCHAR(255),
    payload       JSONB NOT NULL,
    event_type    VARCHAR(255) NOT NULL,
    status        VARCHAR(32) NOT NULL,
    attempts      INTEGER NOT NULL DEFAULT 0,
    last_error    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at  TIMESTAMPTZ
);
CREATE INDEX ix_outbox_events_status ON outbox_events (status);
CREATE INDEX ix_outbox_events_tenant ON outbox_events (tenant_id);

CREATE TABLE payment_intents (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    reference              VARCHAR(255) NOT NULL UNIQUE,
    order_id               UUID,
    payer_org_id           UUID,
    payee_org_id           UUID,
    amount                 NUMERIC(18,4) NOT NULL,
    currency               VARCHAR(255) NOT NULL,
    method                 VARCHAR(64) NOT NULL,
    purpose                VARCHAR(64) NOT NULL,
    payer_phone            VARCHAR(255),
    status                 VARCHAR(64) NOT NULL,
    idempotency_key        VARCHAR(255) NOT NULL UNIQUE,
    initiated_at           TIMESTAMPTZ NOT NULL,
    completed_at           TIMESTAMPTZ,
    failure_reason         VARCHAR(255),
    provider_ref           VARCHAR(255),
    escrow                 BOOLEAN NOT NULL
);
CREATE INDEX ix_payment_intents_tenant ON payment_intents (tenant_id);
CREATE INDEX ix_payment_intents_order_id ON payment_intents (order_id);
CREATE INDEX ix_payment_intents_payer_org_id ON payment_intents (payer_org_id);
CREATE INDEX ix_payment_intents_payee_org_id ON payment_intents (payee_org_id);
CREATE INDEX ix_payment_intents_provider_ref ON payment_intents (provider_ref);

CREATE TABLE mpesa_transactions (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    payment_intent_id      UUID,
    merchant_request_id    VARCHAR(255),
    checkout_request_id    VARCHAR(255),
    mpesa_receipt_number   VARCHAR(255) UNIQUE,
    phone_number           VARCHAR(255),
    amount                 NUMERIC(18,4) NOT NULL,
    transaction_type       VARCHAR(64) NOT NULL,
    result_code            INTEGER,
    result_desc            VARCHAR(255),
    transaction_date       TIMESTAMPTZ,
    account_reference      VARCHAR(255),
    raw_callback           JSONB,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_mpesa_transactions_tenant ON mpesa_transactions (tenant_id);
CREATE INDEX ix_mpesa_transactions_payment_intent_id ON mpesa_transactions (payment_intent_id);
CREATE INDEX ix_mpesa_transactions_merchant_request_id ON mpesa_transactions (merchant_request_id);
CREATE INDEX ix_mpesa_transactions_checkout_request_id ON mpesa_transactions (checkout_request_id);
CREATE INDEX ix_mpesa_transactions_phone_number ON mpesa_transactions (phone_number);

CREATE TABLE escrow_holds (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    payment_intent_id      UUID NOT NULL,
    order_id               UUID NOT NULL,
    amount                 NUMERIC(18,4) NOT NULL,
    currency               VARCHAR(255) NOT NULL,
    held_at                TIMESTAMPTZ NOT NULL,
    release_due_at         TIMESTAMPTZ,
    released_at            TIMESTAMPTZ,
    released_to            UUID,
    refunded_at            TIMESTAMPTZ,
    status                 VARCHAR(64) NOT NULL,
    release_condition      VARCHAR(255)
);
CREATE INDEX ix_escrow_holds_tenant ON escrow_holds (tenant_id);
CREATE INDEX ix_escrow_holds_payment_intent_id ON escrow_holds (payment_intent_id);
CREATE INDEX ix_escrow_holds_order_id ON escrow_holds (order_id);

CREATE TABLE wallets (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    owner_org_id           UUID,
    owner_user_id          UUID,
    currency               VARCHAR(255) NOT NULL,
    balance                NUMERIC(18,4) NOT NULL,
    available_balance      NUMERIC(18,4) NOT NULL,
    status                 VARCHAR(64) NOT NULL,
    last_transaction_at    TIMESTAMPTZ
);
CREATE INDEX ix_wallets_tenant ON wallets (tenant_id);
CREATE INDEX ix_wallets_owner_org_id ON wallets (owner_org_id);
CREATE INDEX ix_wallets_owner_user_id ON wallets (owner_user_id);

CREATE TABLE provider_callbacks (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    provider               VARCHAR(255) NOT NULL,
    callback_type          VARCHAR(255) NOT NULL,
    external_ref           VARCHAR(255),
    signature              VARCHAR(255),
    payload                JSONB NOT NULL,
    received_at            TIMESTAMPTZ NOT NULL,
    processed_at           TIMESTAMPTZ,
    status                 VARCHAR(64) NOT NULL,
    error                  TEXT
);
CREATE INDEX ix_provider_callbacks_tenant ON provider_callbacks (tenant_id);
CREATE INDEX ix_provider_callbacks_provider ON provider_callbacks (provider);
CREATE INDEX ix_provider_callbacks_external_ref ON provider_callbacks (external_ref);
