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

CREATE TABLE search_documents (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    index_name             VARCHAR(255) NOT NULL,
    doc_id                 VARCHAR(255) NOT NULL,
    doc_type               VARCHAR(255) NOT NULL,
    title                  VARCHAR(255),
    body                   TEXT,
    keywords               TEXT,
    county                 VARCHAR(255),
    commodity_code         VARCHAR(255),
    latitude               NUMERIC(18,4),
    longitude              NUMERIC(18,4),
    boost                  NUMERIC(18,4),
    payload                JSONB,
    indexed_at             TIMESTAMPTZ NOT NULL,
    status                 VARCHAR(64) NOT NULL
);
CREATE INDEX ix_search_documents_tenant ON search_documents (tenant_id);
CREATE INDEX ix_search_documents_index_name ON search_documents (index_name);
CREATE INDEX ix_search_documents_doc_id ON search_documents (doc_id);
CREATE INDEX ix_search_documents_county ON search_documents (county);
CREATE INDEX ix_search_documents_commodity_code ON search_documents (commodity_code);

CREATE TABLE index_jobs (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    index_name             VARCHAR(255) NOT NULL,
    job_type               VARCHAR(64) NOT NULL,
    source_event           VARCHAR(255),
    documents_processed    INTEGER NOT NULL,
    started_at             TIMESTAMPTZ NOT NULL,
    completed_at           TIMESTAMPTZ,
    status                 VARCHAR(64) NOT NULL,
    error                  TEXT
);
CREATE INDEX ix_index_jobs_tenant ON index_jobs (tenant_id);
CREATE INDEX ix_index_jobs_index_name ON index_jobs (index_name);
