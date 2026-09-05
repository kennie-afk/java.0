CREATE TABLE devices (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    serial_number          VARCHAR(255) NOT NULL UNIQUE,
    device_type            VARCHAR(64) NOT NULL,
    plot_id                UUID,
    farm_id                UUID,
    model                  VARCHAR(255),
    firmware_version       VARCHAR(255),
    status                 VARCHAR(64) NOT NULL,
    last_seen_at           TIMESTAMPTZ,
    latitude               NUMERIC(18,4),
    longitude              NUMERIC(18,4)
);
CREATE INDEX ix_devices_tenant ON devices (tenant_id);
CREATE INDEX ix_devices_plot_id ON devices (plot_id);
CREATE INDEX ix_devices_farm_id ON devices (farm_id);

CREATE TABLE device_credentials (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    device_id              UUID NOT NULL,
    credential_type        VARCHAR(64) NOT NULL,
    public_key             TEXT,
    fingerprint            VARCHAR(255) UNIQUE,
    issued_at              TIMESTAMPTZ NOT NULL,
    expires_at             TIMESTAMPTZ,
    revoked_at             TIMESTAMPTZ
);
CREATE INDEX ix_device_credentials_tenant ON device_credentials (tenant_id);
CREATE INDEX ix_device_credentials_device_id ON device_credentials (device_id);

CREATE TABLE firmware_releases (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    device_type            VARCHAR(255) NOT NULL,
    release_version        VARCHAR(255) NOT NULL,
    artifact_url           VARCHAR(255) NOT NULL,
    checksum               VARCHAR(255) NOT NULL,
    release_notes          TEXT,
    mandatory              BOOLEAN NOT NULL,
    published_at           TIMESTAMPTZ
);
CREATE INDEX ix_firmware_releases_tenant ON firmware_releases (tenant_id);
CREATE INDEX ix_firmware_releases_device_type ON firmware_releases (device_type);
