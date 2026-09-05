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

CREATE TABLE automation_rules (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    name                   VARCHAR(255) NOT NULL,
    plot_id                UUID,
    trigger_metric         VARCHAR(255),
    operator               VARCHAR(64) NOT NULL,
    threshold              NUMERIC(18,4),
    action_type            VARCHAR(64) NOT NULL,
    action_target_device_id UUID,
    duration_seconds       INTEGER,
    cooldown_seconds       INTEGER NOT NULL,
    enabled                BOOLEAN NOT NULL,
    last_triggered_at      TIMESTAMPTZ
);
CREATE INDEX ix_automation_rules_tenant ON automation_rules (tenant_id);
CREATE INDEX ix_automation_rules_plot_id ON automation_rules (plot_id);

CREATE TABLE actuator_commands (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    device_id              UUID NOT NULL,
    rule_id                UUID,
    command_key            VARCHAR(255) NOT NULL UNIQUE,
    action                 VARCHAR(255) NOT NULL,
    payload                JSONB,
    issued_at              TIMESTAMPTZ NOT NULL,
    acknowledged_at        TIMESTAMPTZ,
    completed_at           TIMESTAMPTZ,
    status                 VARCHAR(64) NOT NULL,
    attempts               INTEGER NOT NULL,
    failure_reason         VARCHAR(255)
);
CREATE INDEX ix_actuator_commands_tenant ON actuator_commands (tenant_id);
CREATE INDEX ix_actuator_commands_device_id ON actuator_commands (device_id);
CREATE INDEX ix_actuator_commands_rule_id ON actuator_commands (rule_id);

CREATE TABLE digital_twins (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    plot_id                UUID NOT NULL UNIQUE,
    soil_moisture_pct      NUMERIC(18,4),
    soil_temp_c            NUMERIC(18,4),
    canopy_index           NUMERIC(18,4),
    irrigation_state       VARCHAR(64) NOT NULL,
    last_irrigated_at      TIMESTAMPTZ,
    updated_from_event_at  TIMESTAMPTZ,
    state                  JSONB
);
CREATE INDEX ix_digital_twins_tenant ON digital_twins (tenant_id);

CREATE TABLE safety_interlocks (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    device_id              UUID NOT NULL,
    interlock_type         VARCHAR(64) NOT NULL,
    max_runtime_seconds    INTEGER,
    conflicting_device_id  UUID,
    engaged                BOOLEAN NOT NULL,
    engaged_at             TIMESTAMPTZ,
    reason                 VARCHAR(255)
);
CREATE INDEX ix_safety_interlocks_tenant ON safety_interlocks (tenant_id);
CREATE INDEX ix_safety_interlocks_device_id ON safety_interlocks (device_id);
