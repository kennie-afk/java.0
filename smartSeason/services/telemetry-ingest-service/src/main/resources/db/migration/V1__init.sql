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

CREATE TABLE telemetry_readings (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    device_id              UUID NOT NULL,
    plot_id                UUID,
    metric                 VARCHAR(255) NOT NULL,
    value                  NUMERIC(18,4) NOT NULL,
    unit                   VARCHAR(255),
    recorded_at            TIMESTAMPTZ NOT NULL,
    received_at            TIMESTAMPTZ NOT NULL,
    quality                VARCHAR(64) NOT NULL,
    raw                    JSONB
);
CREATE INDEX ix_telemetry_readings_tenant ON telemetry_readings (tenant_id);
CREATE INDEX ix_telemetry_readings_device_id ON telemetry_readings (device_id);
CREATE INDEX ix_telemetry_readings_plot_id ON telemetry_readings (plot_id);
CREATE INDEX ix_telemetry_readings_metric ON telemetry_readings (metric);

CREATE TABLE telemetry_anomalies (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    device_id              UUID NOT NULL,
    plot_id                UUID,
    metric                 VARCHAR(255) NOT NULL,
    observed_value         NUMERIC(18,4) NOT NULL,
    expected_min           NUMERIC(18,4),
    expected_max           NUMERIC(18,4),
    detected_at            TIMESTAMPTZ NOT NULL,
    severity               VARCHAR(64) NOT NULL,
    resolved               BOOLEAN NOT NULL
);
CREATE INDEX ix_telemetry_anomalies_tenant ON telemetry_anomalies (tenant_id);
CREATE INDEX ix_telemetry_anomalies_device_id ON telemetry_anomalies (device_id);
CREATE INDEX ix_telemetry_anomalies_plot_id ON telemetry_anomalies (plot_id);

CREATE TABLE downsampled_readings (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    device_id              UUID NOT NULL,
    metric                 VARCHAR(255) NOT NULL,
    bucket_start           TIMESTAMPTZ NOT NULL,
    bucket_minutes         INTEGER NOT NULL,
    avg_value              NUMERIC(18,4),
    min_value              NUMERIC(18,4),
    max_value              NUMERIC(18,4),
    sample_count           INTEGER NOT NULL
);
CREATE INDEX ix_downsampled_readings_tenant ON downsampled_readings (tenant_id);
CREATE INDEX ix_downsampled_readings_device_id ON downsampled_readings (device_id);
