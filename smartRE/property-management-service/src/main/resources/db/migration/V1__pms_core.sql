CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE units (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id       UUID         NOT NULL,
    landlord_id       UUID         NOT NULL,
    label             VARCHAR(40)  NOT NULL,
    unit_type         VARCHAR(32),
    bedrooms          INT,
    bathrooms         INT,
    size_sqm          NUMERIC(10,2),
    rent_amount       NUMERIC(14,2) NOT NULL,
    deposit_amount    NUMERIC(14,2),
    status            VARCHAR(24)  NOT NULL DEFAULT 'VACANT',
    notes             TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_unit_property_label UNIQUE (property_id, label),
    CONSTRAINT ck_unit_status CHECK (status IN ('VACANT','OCCUPIED','UNDER_MAINTENANCE','RESERVED')),
    CONSTRAINT ck_unit_rent_positive CHECK (rent_amount >= 0)
);

CREATE INDEX idx_units_property  ON units (property_id);
CREATE INDEX idx_units_landlord  ON units (landlord_id);
CREATE INDEX idx_units_status    ON units (status);

CREATE TABLE tenants (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    landlord_id        UUID         NOT NULL,
    user_id            UUID,
    full_name          VARCHAR(160) NOT NULL,
    phone              VARCHAR(32)  NOT NULL,
    email              VARCHAR(255),
    national_id        VARCHAR(40),
    emergency_name     VARCHAR(160),
    emergency_phone    VARCHAR(32),
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tenant_landlord_phone UNIQUE (landlord_id, phone)
);

CREATE INDEX idx_tenants_landlord ON tenants (landlord_id);
CREATE INDEX idx_tenants_user     ON tenants (user_id) WHERE user_id IS NOT NULL;

CREATE TABLE leases (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id                UUID         NOT NULL REFERENCES units(id),
    tenant_id              UUID         NOT NULL REFERENCES tenants(id),
    landlord_id            UUID         NOT NULL,
    start_date             DATE         NOT NULL,
    end_date               DATE,
    rent_amount            NUMERIC(14,2) NOT NULL,
    deposit_amount         NUMERIC(14,2) NOT NULL DEFAULT 0,
    deposit_held           NUMERIC(14,2) NOT NULL DEFAULT 0,
    management_fee_pct     NUMERIC(5,2)  NOT NULL DEFAULT 0,
    billing_day            INT          NOT NULL DEFAULT 1,
    payment_frequency      VARCHAR(16)  NOT NULL DEFAULT 'MONTHLY',
    notice_period_days     INT          NOT NULL DEFAULT 30,
    status                 VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    terminated_reason      TEXT,
    terminated_at          TIMESTAMP,
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_lease_status CHECK (status IN ('DRAFT','ACTIVE','ENDED','TERMINATED','RENEWED')),
    CONSTRAINT ck_lease_frequency CHECK (payment_frequency IN ('MONTHLY','QUARTERLY','ANNUALLY')),
    CONSTRAINT ck_lease_billing_day CHECK (billing_day BETWEEN 1 AND 28),
    CONSTRAINT ck_lease_fee_pct CHECK (management_fee_pct >= 0 AND management_fee_pct <= 100),
    CONSTRAINT ck_lease_dates CHECK (end_date IS NULL OR end_date > start_date)
);

CREATE INDEX idx_leases_unit     ON leases (unit_id);
CREATE INDEX idx_leases_tenant   ON leases (tenant_id);
CREATE INDEX idx_leases_landlord ON leases (landlord_id);
CREATE INDEX idx_leases_status   ON leases (status);

CREATE UNIQUE INDEX uq_lease_one_active_per_unit ON leases (unit_id) WHERE status = 'ACTIVE';
