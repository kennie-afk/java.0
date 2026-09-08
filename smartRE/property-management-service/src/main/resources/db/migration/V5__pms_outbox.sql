-- Transactional outbox for pms-events.
--
-- All seven property-management events were published in-band: a failed send was logged
-- and lost, and a send that succeeded before its transaction rolled back announced
-- something that never happened. The consequences are directly financial — a lost
-- RENT_INVOICE_ISSUED means a tenant is never told they owe rent but is still marked
-- overdue later; a lost RENT_RECEIVED means a payment the landlord took is never
-- acknowledged to the tenant.
--
-- aggregate_id is deliberately loose: it is the leaseId, invoiceId or requestId
-- depending on the event, and exists for operators reading the table by hand, not for
-- joins. message_key is the value that must be reproduced exactly on retry — two event
-- types append a discriminator to it so that a repeated overdue notice or a second
-- resolution of the same request keys distinctly.
CREATE TABLE IF NOT EXISTS pms_outbox_events (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID         NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_pms_outbox_unpublished ON pms_outbox_events(published, created_at);
CREATE INDEX IF NOT EXISTS idx_pms_outbox_aggregate   ON pms_outbox_events(aggregate_id);
