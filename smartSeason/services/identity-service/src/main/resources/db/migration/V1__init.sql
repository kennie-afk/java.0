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

CREATE TABLE organisations (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    name                   VARCHAR(255) NOT NULL,
    org_type               VARCHAR(64) NOT NULL,
    county                 VARCHAR(255),
    registration_no        VARCHAR(255) UNIQUE,
    phone                  VARCHAR(255),
    email                  VARCHAR(255),
    status                 VARCHAR(64) NOT NULL,
    kyc_status             VARCHAR(64) NOT NULL
);
CREATE INDEX ix_organisations_tenant ON organisations (tenant_id);

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    email                  VARCHAR(255) NOT NULL UNIQUE,
    phone                  VARCHAR(255),
    full_name              VARCHAR(255) NOT NULL,
    password_hash          VARCHAR(255) NOT NULL,
    organisation_id        UUID,
    roles                  VARCHAR(255) NOT NULL,
    status                 VARCHAR(64) NOT NULL,
    mfa_enabled            BOOLEAN NOT NULL,
    last_login_at          TIMESTAMPTZ,
    failed_attempts        INTEGER NOT NULL,
    locale                 VARCHAR(255)
);
CREATE INDEX ix_users_tenant ON users (tenant_id);
CREATE INDEX ix_users_phone ON users (phone);
CREATE INDEX ix_users_organisation_id ON users (organisation_id);

CREATE TABLE refresh_tokens (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    user_id                UUID NOT NULL,
    token_hash             VARCHAR(255) NOT NULL UNIQUE,
    expires_at             TIMESTAMPTZ NOT NULL,
    revoked_at             TIMESTAMPTZ,
    user_agent             VARCHAR(255),
    ip                     VARCHAR(255)
);
CREATE INDEX ix_refresh_tokens_tenant ON refresh_tokens (tenant_id);
CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE kyc_records (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    subject_id             UUID NOT NULL,
    subject_type           VARCHAR(64) NOT NULL,
    id_number              VARCHAR(255),
    document_url           VARCHAR(255),
    status                 VARCHAR(64) NOT NULL,
    reviewed_by            UUID,
    review_notes           TEXT
);
CREATE INDEX ix_kyc_records_tenant ON kyc_records (tenant_id);
CREATE INDEX ix_kyc_records_subject_id ON kyc_records (subject_id);

CREATE TABLE otp_challenges (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    user_id                UUID,
    destination            VARCHAR(255) NOT NULL,
    channel                VARCHAR(64) NOT NULL,
    code_hash              VARCHAR(255) NOT NULL,
    purpose                VARCHAR(255) NOT NULL,
    expires_at             TIMESTAMPTZ NOT NULL,
    consumed_at            TIMESTAMPTZ,
    attempts               INTEGER NOT NULL
);
CREATE INDEX ix_otp_challenges_tenant ON otp_challenges (tenant_id);
CREATE INDEX ix_otp_challenges_user_id ON otp_challenges (user_id);
