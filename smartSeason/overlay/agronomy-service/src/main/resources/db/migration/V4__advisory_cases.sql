-- Agronomist cases: a farmer's question, the thread under it, and who answers.
--
-- Built for an organisation with thousands of farms and a handful of agronomists:
--  * the queue index matches the queue's ORDER BY exactly, so the open list is read from the
--    index in order and never sorted;
--  * the open-case index is partial: answered and closed cases, which are most of the table
--    after a few months, are not in it, so it stays small;
--  * photos live in their own table so listing cases never drags image bytes through memory.
-- The hot queries are written with the status as a literal (native SQL in CaseRepository). With a
-- bound parameter Postgres may choose a generic plan that cannot prove the partial-index condition,
-- and a 0.3 ms read becomes a 100 ms scan; a literal makes the index usable under any plan mode.
-- Row-level security is applied to every table with a tenant_id by afterMigrate__tenant_isolation.

CREATE TABLE advisory_cases (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version           BIGINT NOT NULL DEFAULT 0,
    plot_id           UUID,
    season_id         UUID,
    crop_code         VARCHAR(64),
    county            VARCHAR(128),
    raised_by         UUID NOT NULL,
    raised_by_name    VARCHAR(255),
    raised_by_email   VARCHAR(255),
    question          TEXT NOT NULL,
    reason            VARCHAR(32) NOT NULL,
    ai_source         VARCHAR(32),
    ai_summary        TEXT,
    ai_confidence     DOUBLE PRECISION,
    ai_severity       VARCHAR(32),
    priority          INTEGER NOT NULL,
    status            VARCHAR(32) NOT NULL,
    assigned_to       UUID,
    assigned_at       TIMESTAMPTZ,
    due_at            TIMESTAMPTZ NOT NULL,
    answered_at       TIMESTAMPTZ,
    closed_at         TIMESTAMPTZ,
    advisory_id       UUID,
    photo_count       INTEGER NOT NULL DEFAULT 0,
    farmer_unread     BOOLEAN NOT NULL DEFAULT FALSE,
    agronomist_unread BOOLEAN NOT NULL DEFAULT FALSE
);

-- The unassigned queue and an agronomist's own list, most urgent first, oldest first within a level.
CREATE INDEX ix_cases_queue ON advisory_cases (tenant_id, priority DESC, created_at ASC, id ASC)
    WHERE status = 'OPEN';
CREATE INDEX ix_cases_assignee ON advisory_cases (tenant_id, assigned_to, priority DESC, created_at ASC, id ASC)
    WHERE status = 'ASSIGNED';
-- An agronomist's answered cases (waiting for the farmer), same order as the other lists.
CREATE INDEX ix_cases_answered ON advisory_cases (tenant_id, assigned_to, priority DESC, created_at ASC, id ASC)
    WHERE status = 'ANSWERED';
-- "My questions", newest first.
CREATE INDEX ix_cases_raiser ON advisory_cases (tenant_id, raised_by, created_at DESC, id DESC);
-- Overdue counting and similar-case lookups.
CREATE INDEX ix_cases_due ON advisory_cases (tenant_id, due_at) WHERE status IN ('OPEN', 'ASSIGNED');
CREATE INDEX ix_cases_similar ON advisory_cases (tenant_id, county, crop_code) WHERE status IN ('OPEN', 'ASSIGNED');

CREATE TABLE case_messages (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    case_id       UUID NOT NULL REFERENCES advisory_cases (id) ON DELETE CASCADE,
    author_id     UUID NOT NULL,
    author_name   VARCHAR(255),
    author_role   VARCHAR(32) NOT NULL,
    body          TEXT NOT NULL
);
CREATE INDEX ix_case_messages_case ON case_messages (tenant_id, case_id, created_at ASC, id ASC);

CREATE TABLE case_photos (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    case_id       UUID NOT NULL REFERENCES advisory_cases (id) ON DELETE CASCADE,
    content_type  VARCHAR(64) NOT NULL,
    size_bytes    INTEGER NOT NULL,
    data          BYTEA NOT NULL
);
CREATE INDEX ix_case_photos_case ON case_photos (tenant_id, case_id);

-- Who answers, for which counties, and how many cases at once.
CREATE TABLE agronomist_profiles (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    user_id       UUID NOT NULL,
    full_name     VARCHAR(255),
    email         VARCHAR(255),
    counties      VARCHAR(1024) NOT NULL DEFAULT '',
    max_open      INTEGER NOT NULL DEFAULT 25,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (tenant_id, user_id)
);
