-- Transactional outbox for viewing-events.
--
-- Before this table, ViewingEventPublisher sent VIEWING_COMPLETED to Kafka in-band with
-- the transaction that marked the viewing complete. Two ways that loses an event: the
-- send fails and the failure is only logged, or the send succeeds and the surrounding
-- transaction then rolls back, so Kafka carries an event for a viewing that was never
-- completed. Downstream, a lost VIEWING_COMPLETED means the buyer is never allowed to
-- review the property, and nothing in the system notices.
--
-- The row is now written in the same transaction as the status change, and the Kafka
-- send happens after that transaction commits. ViewingOutboxSweeper retries anything
-- still unpublished, so a broker outage delays events rather than dropping them.
CREATE TABLE IF NOT EXISTS viewing_outbox_events (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    viewing_id   UUID         NOT NULL,
    event_type   VARCHAR(60)  NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    last_error   TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

-- The sweeper's only query: unpublished rows older than a grace period.
CREATE INDEX IF NOT EXISTS idx_viewing_outbox_unpublished ON viewing_outbox_events(published, created_at);
CREATE INDEX IF NOT EXISTS idx_viewing_outbox_viewing     ON viewing_outbox_events(viewing_id);
