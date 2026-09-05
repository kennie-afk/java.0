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

CREATE TABLE supply_listings (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    seller_org_id          UUID NOT NULL,
    farm_id                UUID,
    commodity_code         VARCHAR(255) NOT NULL,
    variety                VARCHAR(255),
    grade                  VARCHAR(255),
    quantity               NUMERIC(18,4) NOT NULL,
    unit                   VARCHAR(255) NOT NULL,
    ask_price              NUMERIC(18,4) NOT NULL,
    currency               VARCHAR(255) NOT NULL,
    available_from         DATE,
    available_to           DATE,
    county                 VARCHAR(255),
    latitude               NUMERIC(18,4),
    longitude              NUMERIC(18,4),
    batch_id               UUID,
    photo_urls             TEXT,
    description            TEXT,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_supply_listings_tenant ON supply_listings (tenant_id);
CREATE INDEX ix_supply_listings_seller_org_id ON supply_listings (seller_org_id);
CREATE INDEX ix_supply_listings_farm_id ON supply_listings (farm_id);
CREATE INDEX ix_supply_listings_commodity_code ON supply_listings (commodity_code);
CREATE INDEX ix_supply_listings_county ON supply_listings (county);

CREATE TABLE demand_posts (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    buyer_org_id           UUID NOT NULL,
    commodity_code         VARCHAR(255) NOT NULL,
    grade                  VARCHAR(255),
    quantity               NUMERIC(18,4) NOT NULL,
    unit                   VARCHAR(255) NOT NULL,
    bid_price              NUMERIC(18,4),
    currency               VARCHAR(255) NOT NULL,
    needed_by              DATE,
    delivery_county        VARCHAR(255),
    recurring              BOOLEAN NOT NULL,
    status                 VARCHAR(64) NOT NULL,
    notes                  TEXT
);
CREATE INDEX ix_demand_posts_tenant ON demand_posts (tenant_id);
CREATE INDEX ix_demand_posts_buyer_org_id ON demand_posts (buyer_org_id);
CREATE INDEX ix_demand_posts_commodity_code ON demand_posts (commodity_code);
CREATE INDEX ix_demand_posts_delivery_county ON demand_posts (delivery_county);

CREATE TABLE offers (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    listing_id             UUID,
    demand_post_id         UUID,
    from_org_id            UUID NOT NULL,
    to_org_id              UUID NOT NULL,
    quantity               NUMERIC(18,4) NOT NULL,
    unit_price             NUMERIC(18,4) NOT NULL,
    currency               VARCHAR(255) NOT NULL,
    expires_at             TIMESTAMPTZ,
    status                 VARCHAR(64) NOT NULL,
    counter_offer_id       UUID,
    message                TEXT,
    responded_at           TIMESTAMPTZ
);
CREATE INDEX ix_offers_tenant ON offers (tenant_id);
CREATE INDEX ix_offers_listing_id ON offers (listing_id);
CREATE INDEX ix_offers_demand_post_id ON offers (demand_post_id);
CREATE INDEX ix_offers_from_org_id ON offers (from_org_id);
CREATE INDEX ix_offers_to_org_id ON offers (to_org_id);

CREATE TABLE market_matches (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    listing_id             UUID NOT NULL,
    demand_post_id         UUID NOT NULL,
    score                  NUMERIC(18,4) NOT NULL,
    matched_at             TIMESTAMPTZ NOT NULL,
    quantity               NUMERIC(18,4),
    order_id               UUID,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_market_matches_tenant ON market_matches (tenant_id);
CREATE INDEX ix_market_matches_listing_id ON market_matches (listing_id);
CREATE INDEX ix_market_matches_demand_post_id ON market_matches (demand_post_id);
