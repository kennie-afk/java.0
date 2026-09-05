CREATE TABLE seasons (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    plot_id                UUID NOT NULL,
    farm_id                UUID,
    crop_code              VARCHAR(255) NOT NULL,
    variety                VARCHAR(255),
    start_date             DATE NOT NULL,
    expected_harvest_date  DATE,
    actual_harvest_date    DATE,
    expected_yield_kg      NUMERIC(18,4),
    actual_yield_kg        NUMERIC(18,4),
    current_stage          VARCHAR(255),
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_seasons_tenant ON seasons (tenant_id);
CREATE INDEX ix_seasons_plot_id ON seasons (plot_id);
CREATE INDEX ix_seasons_farm_id ON seasons (farm_id);

CREATE TABLE stage_templates (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    crop_code              VARCHAR(255) NOT NULL,
    stage_name             VARCHAR(255) NOT NULL,
    sequence               INTEGER NOT NULL,
    duration_days          INTEGER NOT NULL,
    description            TEXT,
    key_activities         TEXT
);
CREATE INDEX ix_stage_templates_tenant ON stage_templates (tenant_id);
CREATE INDEX ix_stage_templates_crop_code ON stage_templates (crop_code);

CREATE TABLE season_stages (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    season_id              UUID NOT NULL,
    stage_name             VARCHAR(255) NOT NULL,
    sequence               INTEGER NOT NULL,
    planned_start          DATE,
    planned_end            DATE,
    actual_start           DATE,
    actual_end             DATE,
    status                 VARCHAR(64) NOT NULL,
    notes                  TEXT
);
CREATE INDEX ix_season_stages_tenant ON season_stages (tenant_id);
CREATE INDEX ix_season_stages_season_id ON season_stages (season_id);

CREATE TABLE planting_plans (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    season_id              UUID NOT NULL,
    seed_rate_kg_ha        NUMERIC(18,4),
    spacing_cm             VARCHAR(255),
    target_population      INTEGER,
    fertiliser_plan        TEXT,
    irrigation_plan        TEXT,
    approved_by            UUID,
    approved_at            TIMESTAMPTZ
);
CREATE INDEX ix_planting_plans_tenant ON planting_plans (tenant_id);
CREATE INDEX ix_planting_plans_season_id ON planting_plans (season_id);
