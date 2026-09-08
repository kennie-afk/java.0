-- Transactional outbox for property-events.
--
-- property-service has never published anything: the topic and the producer config
-- existed, notification-service has always consumed the topic, and nothing was on the
-- other end. The consequence was a silent one — a seller's listings can be suspended by
-- an admin ban or by the fraud-flag threshold, and nobody told the seller. Their property
-- simply stopped appearing in search.
--
-- One row per suspension event, not per listing. A seller with three listings wants to be
-- told once, with the count and the reason, not three times.
CREATE TABLE IF NOT EXISTS property_outbox_events (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id    UUID         NOT NULL,
    event_type   VARCHAR(60)  NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    message_key  VARCHAR(120) NOT NULL,
    payload      TEXT         NOT NULL,
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    last_error   TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_property_outbox_unpublished ON property_outbox_events(published, created_at);
CREATE INDEX IF NOT EXISTS idx_property_outbox_seller      ON property_outbox_events(seller_id);
