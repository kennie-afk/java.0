CREATE TABLE commodities (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    code                   VARCHAR(255) NOT NULL UNIQUE,
    name                   VARCHAR(255) NOT NULL,
    category               VARCHAR(255),
    default_unit           VARCHAR(255) NOT NULL,
    perishable             BOOLEAN NOT NULL,
    shelf_life_days        INTEGER,
    image_url              VARCHAR(255)
);
CREATE INDEX ix_commodities_tenant ON commodities (tenant_id);
CREATE INDEX ix_commodities_category ON commodities (category);

CREATE TABLE products (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    commodity_code         VARCHAR(255) NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    description            TEXT,
    default_grade          VARCHAR(255),
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_products_tenant ON products (tenant_id);
CREATE INDEX ix_products_commodity_code ON products (commodity_code);

CREATE TABLE product_variants (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    product_id             UUID NOT NULL,
    sku                    VARCHAR(255) NOT NULL UNIQUE,
    variant_name           VARCHAR(255) NOT NULL,
    pack_size              NUMERIC(18,4),
    pack_unit              VARCHAR(255),
    grade                  VARCHAR(255),
    active                 BOOLEAN NOT NULL
);
CREATE INDEX ix_product_variants_tenant ON product_variants (tenant_id);
CREATE INDEX ix_product_variants_product_id ON product_variants (product_id);

CREATE TABLE grade_standards (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    commodity_code         VARCHAR(255) NOT NULL,
    grade                  VARCHAR(255) NOT NULL,
    criteria               TEXT NOT NULL,
    min_size_mm            NUMERIC(18,4),
    max_defect_pct         NUMERIC(18,4),
    moisture_pct_max       NUMERIC(18,4),
    revision               INTEGER NOT NULL
);
CREATE INDEX ix_grade_standards_tenant ON grade_standards (tenant_id);
CREATE INDEX ix_grade_standards_commodity_code ON grade_standards (commodity_code);

CREATE TABLE certifications (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    code                   VARCHAR(255) NOT NULL UNIQUE,
    name                   VARCHAR(255) NOT NULL,
    issuing_body           VARCHAR(255),
    description            TEXT,
    validity_months        INTEGER
);
CREATE INDEX ix_certifications_tenant ON certifications (tenant_id);
