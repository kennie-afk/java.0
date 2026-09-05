CREATE TABLE notification_templates (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    code                   VARCHAR(255) NOT NULL,
    channel                VARCHAR(64) NOT NULL,
    locale                 VARCHAR(255) NOT NULL,
    subject                VARCHAR(255),
    body                   TEXT NOT NULL,
    variables              VARCHAR(255),
    active                 BOOLEAN NOT NULL,
    revision               INTEGER NOT NULL
);
CREATE INDEX ix_notification_templates_tenant ON notification_templates (tenant_id);

CREATE TABLE notifications (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    recipient_user_id      UUID,
    recipient_phone        VARCHAR(255),
    recipient_email        VARCHAR(255),
    channel                VARCHAR(64) NOT NULL,
    template_code          VARCHAR(255),
    locale                 VARCHAR(255) NOT NULL,
    subject                VARCHAR(255),
    body                   TEXT NOT NULL,
    payload                JSONB,
    priority               VARCHAR(64) NOT NULL,
    scheduled_for          TIMESTAMPTZ,
    sent_at                TIMESTAMPTZ,
    delivered_at           TIMESTAMPTZ,
    failed_at              TIMESTAMPTZ,
    failure_reason         VARCHAR(255),
    provider_ref           VARCHAR(255),
    attempts               INTEGER NOT NULL,
    status                 VARCHAR(64) NOT NULL,
    idempotency_key        VARCHAR(255) UNIQUE
);
CREATE INDEX ix_notifications_tenant ON notifications (tenant_id);
CREATE INDEX ix_notifications_recipient_user_id ON notifications (recipient_user_id);
CREATE INDEX ix_notifications_recipient_phone ON notifications (recipient_phone);
CREATE INDEX ix_notifications_template_code ON notifications (template_code);

CREATE TABLE ussd_sessions (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    session_id             VARCHAR(255) NOT NULL UNIQUE,
    phone_number           VARCHAR(255) NOT NULL,
    service_code           VARCHAR(255),
    current_menu           VARCHAR(255) NOT NULL,
    menu_stack             VARCHAR(255),
    context                JSONB,
    started_at             TIMESTAMPTZ NOT NULL,
    last_input_at          TIMESTAMPTZ,
    ended_at               TIMESTAMPTZ,
    status                 VARCHAR(64) NOT NULL,
    hops                   INTEGER NOT NULL
);
CREATE INDEX ix_ussd_sessions_tenant ON ussd_sessions (tenant_id);
CREATE INDEX ix_ussd_sessions_phone_number ON ussd_sessions (phone_number);

CREATE TABLE delivery_receipts (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    notification_id        UUID NOT NULL,
    provider               VARCHAR(255) NOT NULL,
    provider_ref           VARCHAR(255),
    status_code            VARCHAR(255),
    status_text            VARCHAR(255),
    received_at            TIMESTAMPTZ NOT NULL,
    raw                    JSONB
);
CREATE INDEX ix_delivery_receipts_tenant ON delivery_receipts (tenant_id);
CREATE INDEX ix_delivery_receipts_notification_id ON delivery_receipts (notification_id);
CREATE INDEX ix_delivery_receipts_provider_ref ON delivery_receipts (provider_ref);
