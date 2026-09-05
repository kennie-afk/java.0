CREATE TABLE farms (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    name                   VARCHAR(255) NOT NULL,
    owner_user_id          UUID,
    county                 VARCHAR(255),
    sub_county             VARCHAR(255),
    ward                   VARCHAR(255),
    latitude               NUMERIC(18,4),
    longitude              NUMERIC(18,4),
    total_area_ha          NUMERIC(18,4),
    status                 VARCHAR(64) NOT NULL,
    cooperative_id         UUID,
    registration_no        VARCHAR(255)
);
CREATE INDEX ix_farms_tenant ON farms (tenant_id);
CREATE INDEX ix_farms_owner_user_id ON farms (owner_user_id);
CREATE INDEX ix_farms_county ON farms (county);
CREATE INDEX ix_farms_cooperative_id ON farms (cooperative_id);

CREATE TABLE plots (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    farm_id                UUID NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    area_ha                NUMERIC(18,4) NOT NULL,
    boundary_geojson       JSONB,
    centroid_lat           NUMERIC(18,4),
    centroid_lng           NUMERIC(18,4),
    irrigated              BOOLEAN NOT NULL,
    current_crop           VARCHAR(255),
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_plots_tenant ON plots (tenant_id);
CREATE INDEX ix_plots_farm_id ON plots (farm_id);

CREATE TABLE soil_profiles (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    plot_id                UUID NOT NULL,
    sampled_at             DATE NOT NULL,
    ph                     NUMERIC(18,4),
    nitrogen_ppm           NUMERIC(18,4),
    phosphorus_ppm         NUMERIC(18,4),
    potassium_ppm          NUMERIC(18,4),
    organic_carbon_pct     NUMERIC(18,4),
    texture                VARCHAR(255),
    lab_name               VARCHAR(255),
    report_url             VARCHAR(255)
);
CREATE INDEX ix_soil_profiles_tenant ON soil_profiles (tenant_id);
CREATE INDEX ix_soil_profiles_plot_id ON soil_profiles (plot_id);

CREATE TABLE farm_memberships (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    farm_id                UUID NOT NULL,
    user_id                UUID NOT NULL,
    role                   VARCHAR(64) NOT NULL,
    invited_by             UUID,
    accepted_at            TIMESTAMPTZ,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_farm_memberships_tenant ON farm_memberships (tenant_id);
CREATE INDEX ix_farm_memberships_farm_id ON farm_memberships (farm_id);
CREATE INDEX ix_farm_memberships_user_id ON farm_memberships (user_id);
