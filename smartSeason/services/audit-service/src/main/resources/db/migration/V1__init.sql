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

CREATE TABLE audit_records (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    sequence               BIGINT NOT NULL,
    service_name           VARCHAR(255) NOT NULL,
    actor_user_id          UUID,
    actor_role             VARCHAR(255),
    action                 VARCHAR(255) NOT NULL,
    resource_type          VARCHAR(255),
    resource_id            VARCHAR(255),
    outcome                VARCHAR(64) NOT NULL,
    occurred_at            TIMESTAMPTZ NOT NULL,
    ip_address             VARCHAR(255),
    user_agent             VARCHAR(255),
    details                JSONB,
    previous_hash          VARCHAR(255),
    record_hash            VARCHAR(255) NOT NULL UNIQUE
);
CREATE INDEX ix_audit_records_tenant ON audit_records (tenant_id);
CREATE INDEX ix_audit_records_service_name ON audit_records (service_name);
CREATE INDEX ix_audit_records_actor_user_id ON audit_records (actor_user_id);
CREATE INDEX ix_audit_records_action ON audit_records (action);
CREATE INDEX ix_audit_records_resource_type ON audit_records (resource_type);
CREATE INDEX ix_audit_records_resource_id ON audit_records (resource_id);

CREATE TABLE audit_anchors (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    anchor_sequence        BIGINT NOT NULL,
    chain_hash             VARCHAR(255) NOT NULL,
    record_count           BIGINT NOT NULL,
    anchored_at            TIMESTAMPTZ NOT NULL,
    external_ref           VARCHAR(255)
);
CREATE INDEX ix_audit_anchors_tenant ON audit_anchors (tenant_id);
