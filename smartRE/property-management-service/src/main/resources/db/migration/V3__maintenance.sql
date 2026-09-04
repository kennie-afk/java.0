CREATE TABLE maintenance_requests (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id           UUID          NOT NULL REFERENCES units(id),
    lease_id          UUID,
    tenant_id         UUID,
    landlord_id       UUID          NOT NULL,
    raised_by         UUID,
    raised_by_role    VARCHAR(16)   NOT NULL DEFAULT 'TENANT',
    reference         VARCHAR(32)   NOT NULL,
    category          VARCHAR(32)   NOT NULL,
    priority          VARCHAR(16)   NOT NULL DEFAULT 'MEDIUM',
    title             VARCHAR(160)  NOT NULL,
    description       TEXT          NOT NULL,
    image_urls        TEXT,
    status            VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    assigned_to       VARCHAR(160),
    resolution_notes  TEXT,
    cost              NUMERIC(14,2),
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    acknowledged_at   TIMESTAMP,
    resolved_at       TIMESTAMP,
    closed_at         TIMESTAMP,
    CONSTRAINT uq_maintenance_reference UNIQUE (reference),
    CONSTRAINT ck_maintenance_status CHECK (status IN ('OPEN','ACKNOWLEDGED','IN_PROGRESS','RESOLVED','CLOSED','REJECTED')),
    CONSTRAINT ck_maintenance_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
    CONSTRAINT ck_maintenance_raised_by_role CHECK (raised_by_role IN ('TENANT','LANDLORD')),
    CONSTRAINT ck_maintenance_cost CHECK (cost IS NULL OR cost >= 0)
);

CREATE INDEX idx_maintenance_landlord ON maintenance_requests (landlord_id, created_at DESC);
CREATE INDEX idx_maintenance_unit     ON maintenance_requests (unit_id, created_at DESC);
CREATE INDEX idx_maintenance_tenant   ON maintenance_requests (tenant_id, created_at DESC);
CREATE INDEX idx_maintenance_open     ON maintenance_requests (landlord_id, priority)
    WHERE status IN ('OPEN','ACKNOWLEDGED','IN_PROGRESS');
