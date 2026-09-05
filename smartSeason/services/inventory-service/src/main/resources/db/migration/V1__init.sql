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

CREATE TABLE warehouses (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    name                   VARCHAR(255) NOT NULL,
    county                 VARCHAR(255),
    latitude               NUMERIC(18,4),
    longitude              NUMERIC(18,4),
    capacity_kg            NUMERIC(18,4),
    cold_chain             BOOLEAN NOT NULL,
    manager_user_id        UUID,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_warehouses_tenant ON warehouses (tenant_id);
CREATE INDEX ix_warehouses_county ON warehouses (county);

CREATE TABLE batches (
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
    received_at            TIMESTAMPTZ,
    warehouse_id           UUID,
    gross_weight_kg        NUMERIC(18,4),
    net_weight_kg          NUMERIC(18,4),
    grade                  VARCHAR(255),
    moisture_pct           NUMERIC(18,4),
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_batches_tenant ON batches (tenant_id);
CREATE INDEX ix_batches_commodity_code ON batches (commodity_code);
CREATE INDEX ix_batches_farm_id ON batches (farm_id);
CREATE INDEX ix_batches_plot_id ON batches (plot_id);
CREATE INDEX ix_batches_season_id ON batches (season_id);
CREATE INDEX ix_batches_warehouse_id ON batches (warehouse_id);

CREATE TABLE stock_items (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    warehouse_id           UUID NOT NULL,
    commodity_code         VARCHAR(255) NOT NULL,
    grade                  VARCHAR(255),
    batch_id               UUID,
    quantity               NUMERIC(18,4) NOT NULL,
    unit                   VARCHAR(255) NOT NULL,
    reserved_quantity      NUMERIC(18,4) NOT NULL,
    expires_on             DATE,
    last_counted_at        TIMESTAMPTZ
);
CREATE INDEX ix_stock_items_tenant ON stock_items (tenant_id);
CREATE INDEX ix_stock_items_warehouse_id ON stock_items (warehouse_id);
CREATE INDEX ix_stock_items_commodity_code ON stock_items (commodity_code);
CREATE INDEX ix_stock_items_batch_id ON stock_items (batch_id);

CREATE TABLE grading_results (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    batch_id               UUID NOT NULL,
    graded_by              UUID,
    graded_at              TIMESTAMPTZ NOT NULL,
    assigned_grade         VARCHAR(255) NOT NULL,
    size_mm                NUMERIC(18,4),
    defect_pct             NUMERIC(18,4),
    moisture_pct           NUMERIC(18,4),
    rejected_kg            NUMERIC(18,4),
    notes                  TEXT,
    standard_version       INTEGER
);
CREATE INDEX ix_grading_results_tenant ON grading_results (tenant_id);
CREATE INDEX ix_grading_results_batch_id ON grading_results (batch_id);

CREATE TABLE reservations (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    stock_item_id          UUID NOT NULL,
    order_id               UUID,
    quantity               NUMERIC(18,4) NOT NULL,
    reserved_at            TIMESTAMPTZ NOT NULL,
    expires_at             TIMESTAMPTZ,
    released_at            TIMESTAMPTZ,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_reservations_tenant ON reservations (tenant_id);
CREATE INDEX ix_reservations_stock_item_id ON reservations (stock_item_id);
CREATE INDEX ix_reservations_order_id ON reservations (order_id);

CREATE TABLE input_issues (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    farm_id                UUID NOT NULL,
    plot_id                UUID,
    season_id              UUID,
    input_code             VARCHAR(255) NOT NULL,
    input_name             VARCHAR(255) NOT NULL,
    quantity               NUMERIC(18,4) NOT NULL,
    unit                   VARCHAR(255) NOT NULL,
    issued_to              UUID,
    issued_by              UUID,
    issued_at              TIMESTAMPTZ NOT NULL,
    unit_cost              NUMERIC(18,4),
    expected_rate_per_ha   NUMERIC(18,4),
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_input_issues_tenant ON input_issues (tenant_id);
CREATE INDEX ix_input_issues_farm_id ON input_issues (farm_id);
CREATE INDEX ix_input_issues_plot_id ON input_issues (plot_id);
CREATE INDEX ix_input_issues_season_id ON input_issues (season_id);
CREATE INDEX ix_input_issues_input_code ON input_issues (input_code);

CREATE TABLE input_consumptions (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    input_issue_id         UUID,
    farm_id                UUID NOT NULL,
    plot_id                UUID,
    season_id              UUID,
    input_code             VARCHAR(255) NOT NULL,
    quantity               NUMERIC(18,4) NOT NULL,
    unit                   VARCHAR(255) NOT NULL,
    applied_at             TIMESTAMPTZ NOT NULL,
    applied_by             UUID,
    area_covered_ha        NUMERIC(18,4),
    evidence_url           VARCHAR(255),
    variance_kg            NUMERIC(18,4)
);
CREATE INDEX ix_input_consumptions_tenant ON input_consumptions (tenant_id);
CREATE INDEX ix_input_consumptions_input_issue_id ON input_consumptions (input_issue_id);
CREATE INDEX ix_input_consumptions_farm_id ON input_consumptions (farm_id);
CREATE INDEX ix_input_consumptions_plot_id ON input_consumptions (plot_id);
CREATE INDEX ix_input_consumptions_season_id ON input_consumptions (season_id);
