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

CREATE TABLE weather_stations (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    external_id            VARCHAR(255) NOT NULL UNIQUE,
    name                   VARCHAR(255) NOT NULL,
    latitude               NUMERIC(18,4) NOT NULL,
    longitude              NUMERIC(18,4) NOT NULL,
    elevation_m            NUMERIC(18,4),
    provider               VARCHAR(255) NOT NULL,
    county                 VARCHAR(255),
    active                 BOOLEAN NOT NULL
);
CREATE INDEX ix_weather_stations_tenant ON weather_stations (tenant_id);
CREATE INDEX ix_weather_stations_county ON weather_stations (county);

CREATE TABLE forecasts (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    geo_cell               VARCHAR(255) NOT NULL,
    forecast_for           DATE NOT NULL,
    issued_at              TIMESTAMPTZ NOT NULL,
    temp_min_c             NUMERIC(18,4),
    temp_max_c             NUMERIC(18,4),
    rainfall_mm            NUMERIC(18,4),
    humidity_pct           NUMERIC(18,4),
    wind_kph               NUMERIC(18,4),
    conditions             VARCHAR(255),
    provider               VARCHAR(255)
);
CREATE INDEX ix_forecasts_tenant ON forecasts (tenant_id);
CREATE INDEX ix_forecasts_geo_cell ON forecasts (geo_cell);

CREATE TABLE weather_alerts (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    geo_cell               VARCHAR(255) NOT NULL,
    alert_type             VARCHAR(64) NOT NULL,
    severity               VARCHAR(64) NOT NULL,
    starts_at              TIMESTAMPTZ NOT NULL,
    ends_at                TIMESTAMPTZ,
    headline               VARCHAR(255) NOT NULL,
    body                   TEXT,
    source                 VARCHAR(255)
);
CREATE INDEX ix_weather_alerts_tenant ON weather_alerts (tenant_id);
CREATE INDEX ix_weather_alerts_geo_cell ON weather_alerts (geo_cell);

CREATE TABLE ndvi_readings (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    plot_id                UUID,
    geo_cell               VARCHAR(255),
    captured_on            DATE NOT NULL,
    ndvi                   NUMERIC(18,4) NOT NULL,
    cloud_cover_pct        NUMERIC(18,4),
    satellite              VARCHAR(255),
    tile_url               VARCHAR(255)
);
CREATE INDEX ix_ndvi_readings_tenant ON ndvi_readings (tenant_id);
CREATE INDEX ix_ndvi_readings_plot_id ON ndvi_readings (plot_id);
CREATE INDEX ix_ndvi_readings_geo_cell ON ndvi_readings (geo_cell);
