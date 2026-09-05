CREATE TABLE media_assets (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    storage_key            VARCHAR(255) NOT NULL UNIQUE,
    original_filename      VARCHAR(255),
    content_type           VARCHAR(255) NOT NULL,
    size_bytes             BIGINT NOT NULL,
    checksum               VARCHAR(255),
    owner_user_id          UUID,
    context                VARCHAR(255),
    context_ref            VARCHAR(255),
    width                  INTEGER,
    height                 INTEGER,
    duration_seconds       INTEGER,
    perceptual_hash        VARCHAR(255),
    exif_timestamp         TIMESTAMPTZ,
    exif_latitude          NUMERIC(18,4),
    exif_longitude         NUMERIC(18,4),
    public_url             VARCHAR(255),
    virus_scanned          BOOLEAN NOT NULL,
    virus_clean            BOOLEAN NOT NULL,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_media_assets_tenant ON media_assets (tenant_id);
CREATE INDEX ix_media_assets_checksum ON media_assets (checksum);
CREATE INDEX ix_media_assets_owner_user_id ON media_assets (owner_user_id);
CREATE INDEX ix_media_assets_context ON media_assets (context);
CREATE INDEX ix_media_assets_context_ref ON media_assets (context_ref);
CREATE INDEX ix_media_assets_perceptual_hash ON media_assets (perceptual_hash);

CREATE TABLE upload_tickets (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    storage_key            VARCHAR(255) NOT NULL UNIQUE,
    upload_url             TEXT NOT NULL,
    method                 VARCHAR(255) NOT NULL,
    requested_by           UUID NOT NULL,
    content_type           VARCHAR(255),
    max_size_bytes         BIGINT,
    expires_at             TIMESTAMPTZ NOT NULL,
    consumed_at            TIMESTAMPTZ,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_upload_tickets_tenant ON upload_tickets (tenant_id);

CREATE TABLE media_variants (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    asset_id               UUID NOT NULL,
    variant_name           VARCHAR(255) NOT NULL,
    storage_key            VARCHAR(255) NOT NULL,
    width                  INTEGER,
    height                 INTEGER,
    size_bytes             BIGINT,
    content_type           VARCHAR(255),
    public_url             VARCHAR(255)
);
CREATE INDEX ix_media_variants_tenant ON media_variants (tenant_id);
CREATE INDEX ix_media_variants_asset_id ON media_variants (asset_id);
