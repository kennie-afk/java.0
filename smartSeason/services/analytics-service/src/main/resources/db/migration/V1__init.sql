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

CREATE TABLE metric_snapshots (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    metric_key             VARCHAR(255) NOT NULL,
    dimension              VARCHAR(255),
    dimension_value        VARCHAR(255),
    period_start           TIMESTAMPTZ NOT NULL,
    period_end             TIMESTAMPTZ NOT NULL,
    granularity            VARCHAR(64) NOT NULL,
    value                  NUMERIC(18,4) NOT NULL,
    unit                   VARCHAR(255),
    computed_at            TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_metric_snapshots_tenant ON metric_snapshots (tenant_id);
CREATE INDEX ix_metric_snapshots_metric_key ON metric_snapshots (metric_key);
CREATE INDEX ix_metric_snapshots_dimension ON metric_snapshots (dimension);
CREATE INDEX ix_metric_snapshots_dimension_value ON metric_snapshots (dimension_value);

CREATE TABLE reports (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    code                   VARCHAR(255) NOT NULL UNIQUE,
    name                   VARCHAR(255) NOT NULL,
    description            TEXT,
    category               VARCHAR(255),
    query_spec             JSONB NOT NULL,
    schedule               VARCHAR(255),
    format                 VARCHAR(64) NOT NULL,
    enabled                BOOLEAN NOT NULL,
    owner_user_id          UUID
);
CREATE INDEX ix_reports_tenant ON reports (tenant_id);
CREATE INDEX ix_reports_category ON reports (category);

CREATE TABLE report_runs (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    report_id              UUID NOT NULL,
    report_code            VARCHAR(255),
    triggered_by           UUID,
    started_at             TIMESTAMPTZ NOT NULL,
    completed_at           TIMESTAMPTZ,
    row_count              INTEGER,
    output_url             VARCHAR(255),
    parameters             JSONB,
    status                 VARCHAR(64) NOT NULL,
    error                  TEXT
);
CREATE INDEX ix_report_runs_tenant ON report_runs (tenant_id);
CREATE INDEX ix_report_runs_report_id ON report_runs (report_id);
CREATE INDEX ix_report_runs_report_code ON report_runs (report_code);

CREATE TABLE dashboard_widgets (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    dashboard_code         VARCHAR(255) NOT NULL,
    title                  VARCHAR(255) NOT NULL,
    widget_type            VARCHAR(64) NOT NULL,
    metric_key             VARCHAR(255),
    query_spec             JSONB,
    position               INTEGER NOT NULL,
    width                  INTEGER,
    config                 JSONB
);
CREATE INDEX ix_dashboard_widgets_tenant ON dashboard_widgets (tenant_id);
CREATE INDEX ix_dashboard_widgets_dashboard_code ON dashboard_widgets (dashboard_code);
