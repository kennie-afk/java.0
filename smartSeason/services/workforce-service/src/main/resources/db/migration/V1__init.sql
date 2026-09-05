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

CREATE TABLE workers (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    national_id            VARCHAR(255),
    full_name              VARCHAR(255) NOT NULL,
    phone                  VARCHAR(255),
    gender                 VARCHAR(64),
    date_of_birth          DATE,
    farm_id                UUID,
    payout_phone           VARCHAR(255),
    payout_account         VARCHAR(255),
    biometric_ref          VARCHAR(255),
    status                 VARCHAR(64) NOT NULL,
    risk_score             INTEGER NOT NULL,
    onboarded_at           TIMESTAMPTZ,
    photo_url              VARCHAR(255)
);
CREATE INDEX ix_workers_tenant ON workers (tenant_id);
CREATE INDEX ix_workers_national_id ON workers (national_id);
CREATE INDEX ix_workers_phone ON workers (phone);
CREATE INDEX ix_workers_farm_id ON workers (farm_id);

CREATE TABLE worker_contracts (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    worker_id              UUID NOT NULL,
    farm_id                UUID NOT NULL,
    contract_type          VARCHAR(64) NOT NULL,
    start_date             DATE NOT NULL,
    end_date               DATE,
    daily_rate             NUMERIC(18,4),
    piece_rate             NUMERIC(18,4),
    piece_unit             VARCHAR(255),
    supervisor_id          UUID,
    status                 VARCHAR(64) NOT NULL,
    terms                  TEXT
);
CREATE INDEX ix_worker_contracts_tenant ON worker_contracts (tenant_id);
CREATE INDEX ix_worker_contracts_worker_id ON worker_contracts (worker_id);
CREATE INDEX ix_worker_contracts_farm_id ON worker_contracts (farm_id);

CREATE TABLE wage_rates (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    farm_id                UUID,
    task_code              VARCHAR(255) NOT NULL,
    rate_type              VARCHAR(64) NOT NULL,
    amount                 NUMERIC(18,4) NOT NULL,
    currency               VARCHAR(255) NOT NULL,
    unit                   VARCHAR(255),
    effective_from         DATE NOT NULL,
    effective_to           DATE
);
CREATE INDEX ix_wage_rates_tenant ON wage_rates (tenant_id);
CREATE INDEX ix_wage_rates_farm_id ON wage_rates (farm_id);
CREATE INDEX ix_wage_rates_task_code ON wage_rates (task_code);

CREATE TABLE gangs (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    name                   VARCHAR(255) NOT NULL,
    farm_id                UUID NOT NULL,
    supervisor_id          UUID,
    target_size            INTEGER,
    status                 VARCHAR(64) NOT NULL,
    notes                  TEXT
);
CREATE INDEX ix_gangs_tenant ON gangs (tenant_id);
CREATE INDEX ix_gangs_farm_id ON gangs (farm_id);
CREATE INDEX ix_gangs_supervisor_id ON gangs (supervisor_id);

CREATE TABLE gang_memberships (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    gang_id                UUID NOT NULL,
    worker_id              UUID NOT NULL,
    joined_at              TIMESTAMPTZ NOT NULL,
    left_at                TIMESTAMPTZ,
    role                   VARCHAR(64) NOT NULL
);
CREATE INDEX ix_gang_memberships_tenant ON gang_memberships (tenant_id);
CREATE INDEX ix_gang_memberships_gang_id ON gang_memberships (gang_id);
CREATE INDEX ix_gang_memberships_worker_id ON gang_memberships (worker_id);
