CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE notification_templates (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code              VARCHAR(64)  NOT NULL,
    channel           VARCHAR(16)  NOT NULL,
    category          VARCHAR(32)  NOT NULL,
    locale            VARCHAR(8)   NOT NULL DEFAULT 'en',
    subject_template  VARCHAR(500),
    body_template     TEXT         NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_template_code_channel_locale UNIQUE (code, channel, locale),
    CONSTRAINT ck_template_channel CHECK (channel IN ('EMAIL','SMS','IN_APP'))
);

CREATE TABLE notifications (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL,
    recipient_email    VARCHAR(255),
    recipient_phone    VARCHAR(32),
    channel            VARCHAR(16)  NOT NULL,
    category           VARCHAR(32)  NOT NULL,
    template_code      VARCHAR(64)  NOT NULL,
    subject            VARCHAR(500),
    body               TEXT,
    status             VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts           INT          NOT NULL DEFAULT 0,
    last_error         TEXT,
    dedup_key          VARCHAR(128) NOT NULL,
    source_event_type  VARCHAR(64),
    source_event_id    VARCHAR(128),
    entity_type        VARCHAR(32),
    entity_id          UUID,
    action_url         VARCHAR(500),
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    next_attempt_at    TIMESTAMP,
    sent_at            TIMESTAMP,
    read_at            TIMESTAMP,
    CONSTRAINT uq_notification_dedup UNIQUE (dedup_key),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('EMAIL','SMS','IN_APP')),
    CONSTRAINT ck_notification_status  CHECK (status IN ('PENDING','SENT','FAILED','SUPPRESSED'))
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL AND channel = 'IN_APP';
CREATE INDEX idx_notifications_retryable ON notifications (next_attempt_at)
    WHERE status IN ('PENDING','FAILED');
CREATE INDEX idx_notifications_source ON notifications (source_event_type, source_event_id);

CREATE TABLE notification_preferences (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL,
    category      VARCHAR(32) NOT NULL,
    email_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    sms_enabled   BOOLEAN     NOT NULL DEFAULT TRUE,
    in_app_enabled BOOLEAN    NOT NULL DEFAULT TRUE,
    updated_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_preference_user_category UNIQUE (user_id, category)
);

CREATE INDEX idx_preferences_user ON notification_preferences (user_id);
