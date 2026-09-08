-- Transactional outbox for verification-events.
--
-- IDENTITY_APPROVED and OWNERSHIP_APPROVED were published in-band with the transaction
-- that approved the verification: a failed send was logged and forgotten, and a
-- successful send followed by a rollback announced an approval that never happened.
-- Both matter here more than most — property-service listens for OWNERSHIP_APPROVED to
-- let a seller list a property at all, so a dropped event leaves a seller verified in
-- one service and unverified in the next, with no error anywhere.
--
-- Unlike the payment outbox, this table carries an explicit message_key. The two event
-- types are partitioned differently — identity events by seller, ownership events by
-- property — and the sweeper must reproduce the original key exactly or a retry lands
-- in a different partition and loses its ordering guarantee.
CREATE TABLE IF NOT EXISTS verification_outbox_events (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    verification_id UUID         NOT NULL,
    event_type      VARCHAR(60)  NOT NULL,
    topic           VARCHAR(100) NOT NULL,
    message_key     VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_verification_outbox_unpublished
    ON verification_outbox_events(published, created_at);
CREATE INDEX IF NOT EXISTS idx_verification_outbox_verification
    ON verification_outbox_events(verification_id);
