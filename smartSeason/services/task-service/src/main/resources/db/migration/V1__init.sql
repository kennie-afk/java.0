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

CREATE TABLE work_orders (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    farm_id                UUID NOT NULL,
    plot_id                UUID,
    season_id              UUID,
    task_code              VARCHAR(255) NOT NULL,
    title                  VARCHAR(255) NOT NULL,
    description            TEXT,
    due_date               DATE,
    priority               VARCHAR(64) NOT NULL,
    estimated_hours        NUMERIC(18,4),
    created_by             UUID,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_work_orders_tenant ON work_orders (tenant_id);
CREATE INDEX ix_work_orders_farm_id ON work_orders (farm_id);
CREATE INDEX ix_work_orders_plot_id ON work_orders (plot_id);
CREATE INDEX ix_work_orders_season_id ON work_orders (season_id);

CREATE TABLE task_assignments (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    work_order_id          UUID NOT NULL,
    worker_id              UUID,
    gang_id                UUID,
    assigned_by            UUID NOT NULL,
    assigned_at            TIMESTAMPTZ NOT NULL,
    accepted_at            TIMESTAMPTZ,
    started_at             TIMESTAMPTZ,
    completed_at           TIMESTAMPTZ,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_task_assignments_tenant ON task_assignments (tenant_id);
CREATE INDEX ix_task_assignments_work_order_id ON task_assignments (work_order_id);
CREATE INDEX ix_task_assignments_worker_id ON task_assignments (worker_id);
CREATE INDEX ix_task_assignments_gang_id ON task_assignments (gang_id);

CREATE TABLE task_evidence (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    assignment_id          UUID NOT NULL,
    work_order_id          UUID,
    evidence_type          VARCHAR(64) NOT NULL,
    media_url              VARCHAR(255),
    perceptual_hash        VARCHAR(255),
    latitude               NUMERIC(18,4),
    longitude              NUMERIC(18,4),
    captured_at            TIMESTAMPTZ,
    exif_timestamp         TIMESTAMPTZ,
    mock_location          BOOLEAN NOT NULL,
    notes                  TEXT,
    verdict                VARCHAR(64) NOT NULL
);
CREATE INDEX ix_task_evidence_tenant ON task_evidence (tenant_id);
CREATE INDEX ix_task_evidence_assignment_id ON task_evidence (assignment_id);
CREATE INDEX ix_task_evidence_work_order_id ON task_evidence (work_order_id);
CREATE INDEX ix_task_evidence_perceptual_hash ON task_evidence (perceptual_hash);

CREATE TABLE checklist_items (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    work_order_id          UUID NOT NULL,
    label                  VARCHAR(255) NOT NULL,
    sequence               INTEGER NOT NULL,
    required               BOOLEAN NOT NULL,
    completed              BOOLEAN NOT NULL,
    completed_at           TIMESTAMPTZ,
    completed_by           UUID
);
CREATE INDEX ix_checklist_items_tenant ON checklist_items (tenant_id);
CREATE INDEX ix_checklist_items_work_order_id ON checklist_items (work_order_id);
