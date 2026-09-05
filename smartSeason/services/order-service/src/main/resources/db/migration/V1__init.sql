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

CREATE TABLE carts (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    buyer_org_id           UUID NOT NULL,
    buyer_user_id          UUID,
    currency               VARCHAR(255) NOT NULL,
    status                 VARCHAR(64) NOT NULL,
    expires_at             TIMESTAMPTZ
);
CREATE INDEX ix_carts_tenant ON carts (tenant_id);
CREATE INDEX ix_carts_buyer_org_id ON carts (buyer_org_id);

CREATE TABLE cart_items (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    cart_id                UUID NOT NULL,
    listing_id             UUID NOT NULL,
    commodity_code         VARCHAR(255) NOT NULL,
    quantity               NUMERIC(18,4) NOT NULL,
    unit                   VARCHAR(255) NOT NULL,
    unit_price             NUMERIC(18,4) NOT NULL,
    seller_org_id          UUID NOT NULL
);
CREATE INDEX ix_cart_items_tenant ON cart_items (tenant_id);
CREATE INDEX ix_cart_items_cart_id ON cart_items (cart_id);
CREATE INDEX ix_cart_items_listing_id ON cart_items (listing_id);

CREATE TABLE orders (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    order_number           VARCHAR(255) NOT NULL UNIQUE,
    buyer_org_id           UUID NOT NULL,
    seller_org_id          UUID NOT NULL,
    currency               VARCHAR(255) NOT NULL,
    subtotal               NUMERIC(18,4) NOT NULL,
    delivery_fee           NUMERIC(18,4) NOT NULL,
    platform_fee           NUMERIC(18,4) NOT NULL,
    total_amount           NUMERIC(18,4) NOT NULL,
    placed_at              TIMESTAMPTZ NOT NULL,
    confirmed_at           TIMESTAMPTZ,
    fulfilled_at           TIMESTAMPTZ,
    cancelled_at           TIMESTAMPTZ,
    cancellation_reason    VARCHAR(255),
    delivery_county        VARCHAR(255),
    delivery_address       TEXT,
    delivery_lat           NUMERIC(18,4),
    delivery_lng           NUMERIC(18,4),
    payment_intent_id      UUID,
    transport_job_id       UUID,
    status                 VARCHAR(64) NOT NULL,
    idempotency_key        VARCHAR(255) UNIQUE
);
CREATE INDEX ix_orders_tenant ON orders (tenant_id);
CREATE INDEX ix_orders_buyer_org_id ON orders (buyer_org_id);
CREATE INDEX ix_orders_seller_org_id ON orders (seller_org_id);
CREATE INDEX ix_orders_payment_intent_id ON orders (payment_intent_id);

CREATE TABLE order_lines (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    order_id               UUID NOT NULL,
    listing_id             UUID,
    commodity_code         VARCHAR(255) NOT NULL,
    grade                  VARCHAR(255),
    quantity               NUMERIC(18,4) NOT NULL,
    unit                   VARCHAR(255) NOT NULL,
    unit_price             NUMERIC(18,4) NOT NULL,
    line_total             NUMERIC(18,4) NOT NULL,
    batch_id               UUID,
    fulfilled_quantity     NUMERIC(18,4) NOT NULL
);
CREATE INDEX ix_order_lines_tenant ON order_lines (tenant_id);
CREATE INDEX ix_order_lines_order_id ON order_lines (order_id);
CREATE INDEX ix_order_lines_listing_id ON order_lines (listing_id);

CREATE TABLE order_saga_states (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    order_id               UUID NOT NULL UNIQUE,
    current_step           VARCHAR(255) NOT NULL,
    step_status            VARCHAR(64) NOT NULL,
    attempts               INTEGER NOT NULL,
    last_error             TEXT,
    started_at             TIMESTAMPTZ NOT NULL,
    last_transition_at     TIMESTAMPTZ,
    completed_at           TIMESTAMPTZ,
    context                JSONB
);
CREATE INDEX ix_order_saga_states_tenant ON order_saga_states (tenant_id);

CREATE TABLE order_returns (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    order_id               UUID NOT NULL,
    order_line_id          UUID,
    quantity               NUMERIC(18,4) NOT NULL,
    reason                 TEXT NOT NULL,
    requested_by           UUID,
    requested_at           TIMESTAMPTZ NOT NULL,
    refund_amount          NUMERIC(18,4),
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_order_returns_tenant ON order_returns (tenant_id);
CREATE INDEX ix_order_returns_order_id ON order_returns (order_id);
CREATE INDEX ix_order_returns_order_line_id ON order_returns (order_line_id);

CREATE TABLE disputes (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    order_id               UUID NOT NULL,
    raised_by_org_id       UUID NOT NULL,
    category               VARCHAR(64) NOT NULL,
    description            TEXT NOT NULL,
    raised_at              TIMESTAMPTZ NOT NULL,
    status                 VARCHAR(64) NOT NULL,
    resolution             TEXT,
    resolved_at            TIMESTAMPTZ,
    resolved_by            UUID
);
CREATE INDEX ix_disputes_tenant ON disputes (tenant_id);
CREATE INDEX ix_disputes_order_id ON disputes (order_id);
