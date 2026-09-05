CREATE TABLE geofences (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    farm_id                UUID NOT NULL,
    plot_id                UUID,
    name                   VARCHAR(255) NOT NULL,
    center_lat             NUMERIC(18,4) NOT NULL,
    center_lng             NUMERIC(18,4) NOT NULL,
    radius_m               INTEGER NOT NULL,
    active                 BOOLEAN NOT NULL
);
CREATE INDEX ix_geofences_tenant ON geofences (tenant_id);
CREATE INDEX ix_geofences_farm_id ON geofences (farm_id);
CREATE INDEX ix_geofences_plot_id ON geofences (plot_id);

CREATE TABLE clock_events (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    worker_id              UUID NOT NULL,
    farm_id                UUID NOT NULL,
    shift_id               UUID,
    event_type             VARCHAR(64) NOT NULL,
    occurred_at            TIMESTAMPTZ NOT NULL,
    recorded_at            TIMESTAMPTZ NOT NULL,
    latitude               NUMERIC(18,4),
    longitude              NUMERIC(18,4),
    accuracy_m             NUMERIC(18,4),
    geofence_id            UUID,
    inside_geofence        BOOLEAN NOT NULL,
    biometric_score        NUMERIC(18,4),
    device_id              VARCHAR(255),
    mock_location          BOOLEAN NOT NULL,
    offline_synced         BOOLEAN NOT NULL,
    client_event_id        VARCHAR(255) UNIQUE,
    verdict                VARCHAR(64) NOT NULL,
    flag_reason            VARCHAR(255)
);
CREATE INDEX ix_clock_events_tenant ON clock_events (tenant_id);
CREATE INDEX ix_clock_events_worker_id ON clock_events (worker_id);
CREATE INDEX ix_clock_events_farm_id ON clock_events (farm_id);
CREATE INDEX ix_clock_events_shift_id ON clock_events (shift_id);

CREATE TABLE shifts (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    worker_id              UUID NOT NULL,
    farm_id                UUID NOT NULL,
    gang_id                UUID,
    started_at             TIMESTAMPTZ NOT NULL,
    ended_at               TIMESTAMPTZ,
    duration_minutes       INTEGER,
    break_minutes          INTEGER NOT NULL,
    supervisor_id          UUID,
    status                 VARCHAR(64) NOT NULL,
    anomaly_flags          VARCHAR(255)
);
CREATE INDEX ix_shifts_tenant ON shifts (tenant_id);
CREATE INDEX ix_shifts_worker_id ON shifts (worker_id);
CREATE INDEX ix_shifts_farm_id ON shifts (farm_id);
CREATE INDEX ix_shifts_gang_id ON shifts (gang_id);

CREATE TABLE piece_rate_entries (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    worker_id              UUID NOT NULL,
    shift_id               UUID,
    farm_id                UUID NOT NULL,
    plot_id                UUID,
    task_code              VARCHAR(255) NOT NULL,
    quantity               NUMERIC(18,4) NOT NULL,
    unit                   VARCHAR(255) NOT NULL,
    recorded_at            TIMESTAMPTZ NOT NULL,
    recorded_by            UUID,
    weigh_station_id       VARCHAR(255),
    verified_by            UUID,
    verified_at            TIMESTAMPTZ,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_piece_rate_entries_tenant ON piece_rate_entries (tenant_id);
CREATE INDEX ix_piece_rate_entries_worker_id ON piece_rate_entries (worker_id);
CREATE INDEX ix_piece_rate_entries_shift_id ON piece_rate_entries (shift_id);
CREATE INDEX ix_piece_rate_entries_farm_id ON piece_rate_entries (farm_id);
CREATE INDEX ix_piece_rate_entries_plot_id ON piece_rate_entries (plot_id);
