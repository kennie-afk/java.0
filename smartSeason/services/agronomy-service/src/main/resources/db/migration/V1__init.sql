CREATE TABLE advisories (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    season_id              UUID,
    plot_id                UUID,
    crop_code              VARCHAR(255),
    title                  VARCHAR(255) NOT NULL,
    body                   TEXT NOT NULL,
    severity               VARCHAR(64) NOT NULL,
    source                 VARCHAR(64) NOT NULL,
    issued_at              TIMESTAMPTZ NOT NULL,
    acknowledged_at        TIMESTAMPTZ,
    acknowledged_by        UUID
);
CREATE INDEX ix_advisories_tenant ON advisories (tenant_id);
CREATE INDEX ix_advisories_season_id ON advisories (season_id);
CREATE INDEX ix_advisories_plot_id ON advisories (plot_id);
CREATE INDEX ix_advisories_crop_code ON advisories (crop_code);

CREATE TABLE pest_diseases (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    code                   VARCHAR(255) NOT NULL UNIQUE,
    common_name            VARCHAR(255) NOT NULL,
    scientific_name        VARCHAR(255),
    type                   VARCHAR(64) NOT NULL,
    affected_crops         VARCHAR(255),
    symptoms               TEXT,
    management             TEXT,
    image_url              VARCHAR(255)
);
CREATE INDEX ix_pest_diseases_tenant ON pest_diseases (tenant_id);

CREATE TABLE scouting_reports (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    plot_id                UUID NOT NULL,
    season_id              UUID,
    scouted_by             UUID NOT NULL,
    scouted_at             TIMESTAMPTZ NOT NULL,
    pest_disease_code      VARCHAR(255),
    incidence_pct          NUMERIC(18,4),
    severity_score         INTEGER,
    latitude               NUMERIC(18,4),
    longitude              NUMERIC(18,4),
    photo_url              VARCHAR(255),
    notes                  TEXT,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_scouting_reports_tenant ON scouting_reports (tenant_id);
CREATE INDEX ix_scouting_reports_plot_id ON scouting_reports (plot_id);
CREATE INDEX ix_scouting_reports_season_id ON scouting_reports (season_id);

CREATE TABLE crop_playbooks (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    crop_code              VARCHAR(255) NOT NULL,
    stage_name             VARCHAR(255) NOT NULL,
    guidance               TEXT NOT NULL,
    input_recommendations  TEXT,
    risk_factors           TEXT,
    revision               INTEGER NOT NULL
);
CREATE INDEX ix_crop_playbooks_tenant ON crop_playbooks (tenant_id);
CREATE INDEX ix_crop_playbooks_crop_code ON crop_playbooks (crop_code);
