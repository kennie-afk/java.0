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

CREATE TABLE price_series (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    commodity_code         VARCHAR(255) NOT NULL,
    county                 VARCHAR(255),
    market_name            VARCHAR(255),
    grade                  VARCHAR(255),
    observed_on            DATE NOT NULL,
    unit                   VARCHAR(255) NOT NULL,
    min_price              NUMERIC(18,4),
    max_price              NUMERIC(18,4),
    avg_price              NUMERIC(18,4) NOT NULL,
    currency               VARCHAR(255) NOT NULL,
    source                 VARCHAR(255),
    volume_kg              NUMERIC(18,4)
);
CREATE INDEX ix_price_series_tenant ON price_series (tenant_id);
CREATE INDEX ix_price_series_commodity_code ON price_series (commodity_code);
CREATE INDEX ix_price_series_county ON price_series (county);

CREATE TABLE market_indices (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    commodity_code         VARCHAR(255) NOT NULL,
    region                 VARCHAR(255) NOT NULL,
    period_start           DATE NOT NULL,
    period_end             DATE NOT NULL,
    index_value            NUMERIC(18,4) NOT NULL,
    change_pct             NUMERIC(18,4),
    basis                  VARCHAR(255),
    computed_at            TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_market_indices_tenant ON market_indices (tenant_id);
CREATE INDEX ix_market_indices_commodity_code ON market_indices (commodity_code);
CREATE INDEX ix_market_indices_region ON market_indices (region);

CREATE TABLE price_quotes (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    commodity_code         VARCHAR(255) NOT NULL,
    grade                  VARCHAR(255),
    county                 VARCHAR(255),
    quantity               NUMERIC(18,4),
    suggested_price        NUMERIC(18,4) NOT NULL,
    confidence             NUMERIC(18,4),
    currency               VARCHAR(255) NOT NULL,
    valid_until            TIMESTAMPTZ,
    rationale              TEXT,
    requested_by           UUID
);
CREATE INDEX ix_price_quotes_tenant ON price_quotes (tenant_id);
CREATE INDEX ix_price_quotes_commodity_code ON price_quotes (commodity_code);
