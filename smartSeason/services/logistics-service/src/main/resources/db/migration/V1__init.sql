CREATE TABLE vehicles (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    registration_no        VARCHAR(255) NOT NULL UNIQUE,
    vehicle_type           VARCHAR(64) NOT NULL,
    capacity_kg            NUMERIC(18,4),
    cold_chain             BOOLEAN NOT NULL,
    owner_org_id           UUID,
    odometer_km            NUMERIC(18,4),
    status                 VARCHAR(64) NOT NULL,
    last_service_at        DATE
);
CREATE INDEX ix_vehicles_tenant ON vehicles (tenant_id);
CREATE INDEX ix_vehicles_owner_org_id ON vehicles (owner_org_id);

CREATE TABLE drivers (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    user_id                UUID,
    full_name              VARCHAR(255) NOT NULL,
    phone                  VARCHAR(255) NOT NULL,
    licence_number         VARCHAR(255) UNIQUE,
    licence_expiry         DATE,
    assigned_vehicle_id    UUID,
    rating                 NUMERIC(18,4),
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_drivers_tenant ON drivers (tenant_id);
CREATE INDEX ix_drivers_user_id ON drivers (user_id);
CREATE INDEX ix_drivers_phone ON drivers (phone);
CREATE INDEX ix_drivers_assigned_vehicle_id ON drivers (assigned_vehicle_id);

CREATE TABLE transport_jobs (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    job_number             VARCHAR(255) NOT NULL UNIQUE,
    order_id               UUID,
    batch_id               UUID,
    vehicle_id             UUID,
    driver_id              UUID,
    pickup_county          VARCHAR(255),
    pickup_lat             NUMERIC(18,4),
    pickup_lng             NUMERIC(18,4),
    pickup_at              TIMESTAMPTZ,
    dropoff_county         VARCHAR(255),
    dropoff_lat            NUMERIC(18,4),
    dropoff_lng            NUMERIC(18,4),
    dropoff_at             TIMESTAMPTZ,
    distance_km            NUMERIC(18,4),
    weight_kg              NUMERIC(18,4),
    freight_cost           NUMERIC(18,4),
    currency               VARCHAR(255),
    requires_cold_chain    BOOLEAN NOT NULL,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_transport_jobs_tenant ON transport_jobs (tenant_id);
CREATE INDEX ix_transport_jobs_order_id ON transport_jobs (order_id);
CREATE INDEX ix_transport_jobs_batch_id ON transport_jobs (batch_id);
CREATE INDEX ix_transport_jobs_vehicle_id ON transport_jobs (vehicle_id);
CREATE INDEX ix_transport_jobs_driver_id ON transport_jobs (driver_id);

CREATE TABLE route_stops (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    transport_job_id       UUID NOT NULL,
    sequence               INTEGER NOT NULL,
    stop_type              VARCHAR(64) NOT NULL,
    latitude               NUMERIC(18,4),
    longitude              NUMERIC(18,4),
    planned_at             TIMESTAMPTZ,
    arrived_at             TIMESTAMPTZ,
    departed_at            TIMESTAMPTZ,
    notes                  TEXT,
    off_route              BOOLEAN NOT NULL
);
CREATE INDEX ix_route_stops_tenant ON route_stops (tenant_id);
CREATE INDEX ix_route_stops_transport_job_id ON route_stops (transport_job_id);

CREATE TABLE proofs_of_delivery (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    transport_job_id       UUID NOT NULL UNIQUE,
    received_by            VARCHAR(255) NOT NULL,
    received_at            TIMESTAMPTZ NOT NULL,
    signature_url          VARCHAR(255),
    photo_url              VARCHAR(255),
    latitude               NUMERIC(18,4),
    longitude              NUMERIC(18,4),
    delivered_weight_kg    NUMERIC(18,4),
    variance_kg            NUMERIC(18,4),
    notes                  TEXT,
    disputed               BOOLEAN NOT NULL
);
CREATE INDEX ix_proofs_of_delivery_tenant ON proofs_of_delivery (tenant_id);

CREATE TABLE cold_chain_readings (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    transport_job_id       UUID NOT NULL,
    recorded_at            TIMESTAMPTZ NOT NULL,
    temperature_c          NUMERIC(18,4) NOT NULL,
    humidity_pct           NUMERIC(18,4),
    device_id              VARCHAR(255),
    breach                 BOOLEAN NOT NULL
);
CREATE INDEX ix_cold_chain_readings_tenant ON cold_chain_readings (tenant_id);
CREATE INDEX ix_cold_chain_readings_transport_job_id ON cold_chain_readings (transport_job_id);
