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

CREATE TABLE trace_batches (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    batch_code             VARCHAR(255) NOT NULL UNIQUE,
    commodity_code         VARCHAR(255) NOT NULL,
    farm_id                UUID,
    plot_id                UUID,
    season_id              UUID,
    harvested_on           DATE,
    origin_county          VARCHAR(255),
    current_holder_org_id  UUID,
    quantity_kg            NUMERIC(18,4),
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_trace_batches_tenant ON trace_batches (tenant_id);
CREATE INDEX ix_trace_batches_commodity_code ON trace_batches (commodity_code);
CREATE INDEX ix_trace_batches_farm_id ON trace_batches (farm_id);
CREATE INDEX ix_trace_batches_plot_id ON trace_batches (plot_id);
CREATE INDEX ix_trace_batches_season_id ON trace_batches (season_id);
CREATE INDEX ix_trace_batches_current_holder_org_id ON trace_batches (current_holder_org_id);

CREATE TABLE trace_links (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    batch_code             VARCHAR(255) NOT NULL,
    sequence               INTEGER NOT NULL,
    node_type              VARCHAR(64) NOT NULL,
    node_ref               VARCHAR(255) NOT NULL,
    occurred_at            TIMESTAMPTZ NOT NULL,
    actor_org_id           UUID,
    location               VARCHAR(255),
    latitude               NUMERIC(18,4),
    longitude              NUMERIC(18,4),
    attributes             JSONB,
    evidence_url           VARCHAR(255)
);
CREATE INDEX ix_trace_links_tenant ON trace_links (tenant_id);
CREATE INDEX ix_trace_links_batch_code ON trace_links (batch_code);

CREATE TABLE qr_passes (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    batch_code             VARCHAR(255) NOT NULL,
    pass_code              VARCHAR(255) NOT NULL UNIQUE,
    qr_url                 VARCHAR(255),
    issued_at              TIMESTAMPTZ NOT NULL,
    expires_at             TIMESTAMPTZ,
    scan_count             INTEGER NOT NULL,
    last_scanned_at        TIMESTAMPTZ,
    public_summary         JSONB,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_qr_passes_tenant ON qr_passes (tenant_id);
CREATE INDEX ix_qr_passes_batch_code ON qr_passes (batch_code);

CREATE TABLE cert_evidence (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    batch_code             VARCHAR(255),
    farm_id                UUID,
    certification_code     VARCHAR(255) NOT NULL,
    certificate_no         VARCHAR(255),
    issued_by              VARCHAR(255),
    issued_on              DATE,
    expires_on             DATE,
    document_url           VARCHAR(255),
    verified               BOOLEAN NOT NULL,
    verified_at            TIMESTAMPTZ
);
CREATE INDEX ix_cert_evidence_tenant ON cert_evidence (tenant_id);
CREATE INDEX ix_cert_evidence_batch_code ON cert_evidence (batch_code);
CREATE INDEX ix_cert_evidence_farm_id ON cert_evidence (farm_id);
