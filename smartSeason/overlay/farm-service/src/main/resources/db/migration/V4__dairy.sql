-- Dairy module: herd, daily yield, buyer deliveries, health and breeding events.
-- Table shapes match what tools/catalogue.py generates; the constraints at the end are
-- the dairy rules the database holds itself, whatever the API does.

CREATE TABLE cows (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    farm_id                UUID NOT NULL,
    tag_no                 VARCHAR(255) NOT NULL,
    name                   VARCHAR(255),
    breed                  VARCHAR(255),
    sex                    VARCHAR(64) NOT NULL,
    birth_date             DATE,
    dam_id                 UUID,
    sire_ref               VARCHAR(255),
    status                 VARCHAR(64) NOT NULL,
    acquired_on            DATE,
    exited_on              DATE,
    notes                  TEXT
);
CREATE INDEX ix_cows_tenant ON cows (tenant_id);
CREATE INDEX ix_cows_farm_id ON cows (farm_id);
CREATE INDEX ix_cows_tag_no ON cows (tag_no);
CREATE INDEX ix_cows_dam_id ON cows (dam_id);

CREATE TABLE milk_yields (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    cow_id                 UUID NOT NULL,
    farm_id                UUID NOT NULL,
    recorded_on            DATE NOT NULL,
    session                VARCHAR(64) NOT NULL,
    litres                 NUMERIC(18,4) NOT NULL,
    recorded_by            UUID,
    notes                  VARCHAR(255)
);
CREATE INDEX ix_milk_yields_tenant ON milk_yields (tenant_id);
CREATE INDEX ix_milk_yields_cow_id ON milk_yields (cow_id);
CREATE INDEX ix_milk_yields_farm_id ON milk_yields (farm_id);
CREATE INDEX ix_milk_yields_recorded_on ON milk_yields (recorded_on);

CREATE TABLE milk_deliveries (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    farm_id                UUID NOT NULL,
    delivered_on           DATE NOT NULL,
    buyer_name             VARCHAR(255) NOT NULL,
    receipt_no             VARCHAR(255),
    litres_delivered       NUMERIC(18,4) NOT NULL,
    litres_rejected        NUMERIC(18,4) NOT NULL,
    fat_pct                NUMERIC(18,4),
    snf_pct                NUMERIC(18,4),
    temperature_c          NUMERIC(18,4),
    alcohol_test_passed    BOOLEAN,
    price_per_litre        NUMERIC(18,4),
    currency               VARCHAR(255),
    status                 VARCHAR(64) NOT NULL,
    notes                  TEXT
);
CREATE INDEX ix_milk_deliveries_tenant ON milk_deliveries (tenant_id);
CREATE INDEX ix_milk_deliveries_farm_id ON milk_deliveries (farm_id);
CREATE INDEX ix_milk_deliveries_delivered_on ON milk_deliveries (delivered_on);
CREATE INDEX ix_milk_deliveries_receipt_no ON milk_deliveries (receipt_no);

CREATE TABLE cow_health_events (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    cow_id                 UUID NOT NULL,
    farm_id                UUID NOT NULL,
    event_date             DATE NOT NULL,
    event_type             VARCHAR(64) NOT NULL,
    description            TEXT,
    medicine               VARCHAR(255),
    withdrawal_ends_on     DATE,
    vet_name               VARCHAR(255),
    cost_amount            NUMERIC(18,4)
);
CREATE INDEX ix_cow_health_events_tenant ON cow_health_events (tenant_id);
CREATE INDEX ix_cow_health_events_cow_id ON cow_health_events (cow_id);
CREATE INDEX ix_cow_health_events_farm_id ON cow_health_events (farm_id);
CREATE INDEX ix_cow_health_events_event_date ON cow_health_events (event_date);
CREATE INDEX ix_cow_health_events_withdrawal_ends_on ON cow_health_events (withdrawal_ends_on);

CREATE TABLE breeding_events (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    cow_id                 UUID NOT NULL,
    farm_id                UUID NOT NULL,
    event_date             DATE NOT NULL,
    event_type             VARCHAR(64) NOT NULL,
    method                 VARCHAR(64),
    sire_ref               VARCHAR(255),
    outcome                VARCHAR(255),
    expected_calving_on    DATE,
    notes                  TEXT
);
CREATE INDEX ix_breeding_events_tenant ON breeding_events (tenant_id);
CREATE INDEX ix_breeding_events_cow_id ON breeding_events (cow_id);
CREATE INDEX ix_breeding_events_farm_id ON breeding_events (farm_id);
CREATE INDEX ix_breeding_events_event_date ON breeding_events (event_date);

CREATE INDEX IF NOT EXISTS ix_cows_keyset
    ON cows (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_milk_yields_keyset
    ON milk_yields (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_milk_deliveries_keyset
    ON milk_deliveries (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_cow_health_events_keyset
    ON cow_health_events (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_breeding_events_keyset
    ON breeding_events (tenant_id, created_at DESC, id DESC);

-- One tag per animal per farm; one reading per cow, day and session.
CREATE UNIQUE INDEX uq_cows_farm_tag ON cows (tenant_id, farm_id, tag_no);
CREATE UNIQUE INDEX uq_milk_yields_cow_day_session ON milk_yields (tenant_id, cow_id, recorded_on, session);
CREATE INDEX ix_milk_yields_farm_day ON milk_yields (tenant_id, farm_id, recorded_on);
CREATE INDEX ix_milk_deliveries_farm_day ON milk_deliveries (tenant_id, farm_id, delivered_on);

ALTER TABLE milk_yields ADD CONSTRAINT ck_milk_yields_litres CHECK (litres >= 0 AND litres <= 100);
ALTER TABLE milk_deliveries ADD CONSTRAINT ck_milk_deliveries_litres
    CHECK (litres_delivered >= 0 AND litres_rejected >= 0 AND litres_rejected <= litres_delivered);
ALTER TABLE milk_deliveries ADD CONSTRAINT ck_milk_deliveries_quality
    CHECK ((fat_pct IS NULL OR (fat_pct >= 0 AND fat_pct <= 100))
       AND (snf_pct IS NULL OR (snf_pct >= 0 AND snf_pct <= 100)));
ALTER TABLE cow_health_events ADD CONSTRAINT ck_cow_health_withdrawal
    CHECK (withdrawal_ends_on IS NULL OR withdrawal_ends_on >= event_date);
