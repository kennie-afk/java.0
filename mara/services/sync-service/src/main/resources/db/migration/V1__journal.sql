-- The server's second copy of every terminal's journal.
--
-- Until this existed a tamper-evident chain on a till was only evidence to the till's own
-- owner: someone with the browser profile could rewrite the tail and the head together. A
-- copy held here, append-only and verified on the way in, is what makes "the server can detect
-- any sale that was altered, back-dated, removed or replayed" true.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mara_app') THEN
        CREATE ROLE mara_app NOLOGIN;
    END IF;
END
$$;

CREATE OR REPLACE FUNCTION current_tenant() RETURNS TEXT
    LANGUAGE sql
    STABLE
    SET search_path = pg_catalog
AS $$
    SELECT nullif(current_setting('mara.tenant_id', true), '')
$$;

-- Where each terminal's verified chain currently ends. Exactly one row per terminal; the
-- ingest transaction takes this row FOR UPDATE, which serialises uploads from one terminal
-- (a retry racing its own original) while terminals never wait on each other.
CREATE TABLE chain_head (
    terminal_id      TEXT PRIMARY KEY,
    tenant_id        TEXT        NOT NULL,
    last_sequence    BIGINT      NOT NULL DEFAULT 0,
    head_digest      BYTEA       NOT NULL,
    genesis_digest   BYTEA       NOT NULL,
    last_epoch_second BIGINT     NOT NULL DEFAULT 0,
    last_nano        INTEGER     NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chain_head_sequence_nonneg CHECK (last_sequence >= 0),
    CONSTRAINT chain_head_digest_size CHECK (octet_length(head_digest) = 32 AND octet_length(genesis_digest) = 32)
);

-- Every verified entry, as the terminal signed it. Never updated, never deleted.
CREATE TABLE journal_entry (
    terminal_id     TEXT        NOT NULL,
    sequence        BIGINT      NOT NULL,
    tenant_id       TEXT        NOT NULL,
    epoch_second    BIGINT      NOT NULL,
    nano            INTEGER     NOT NULL,
    sale            JSONB       NOT NULL,
    body_digest     BYTEA       NOT NULL,
    previous_digest BYTEA       NOT NULL,
    digest          BYTEA       NOT NULL,
    signature       BYTEA       NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (terminal_id, sequence),
    CONSTRAINT journal_entry_sequence_positive CHECK (sequence >= 1),
    CONSTRAINT journal_entry_digest_sizes CHECK (
        octet_length(body_digest) = 32 AND octet_length(previous_digest) = 32 AND octet_length(digest) = 32)
);

CREATE INDEX journal_entry_by_tenant_time ON journal_entry (tenant_id, epoch_second);

CREATE OR REPLACE FUNCTION journal_entry_immutable() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'journal_entry is append-only (% refused)', TG_OP USING ERRCODE = 'integrity_constraint_violation';
END
$$;

CREATE TRIGGER journal_entry_no_update_delete
    BEFORE UPDATE OR DELETE ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION journal_entry_immutable();
CREATE TRIGGER journal_entry_no_truncate
    BEFORE TRUNCATE ON journal_entry
    FOR EACH STATEMENT EXECUTE FUNCTION journal_entry_immutable();

-- Everything the verifier refused or flagged. A hole in a terminal's sequence, a forked
-- sequence, a bad signature, an arithmetic mismatch inside a sale: each becomes a row an
-- owner can see. Repeated retries of the same problem raise one open row, not hundreds.
CREATE TABLE sync_exception (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   TEXT        NOT NULL,
    terminal_id TEXT        NOT NULL,
    sequence    BIGINT      NOT NULL DEFAULT 0,
    kind        TEXT        NOT NULL,
    detail      TEXT        NOT NULL,
    raised_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolution  TEXT,

    CONSTRAINT sync_exception_kind_known CHECK (kind IN (
        'GAP', 'FORKED_SEQUENCE', 'BROKEN_LINK', 'OUT_OF_ORDER', 'UNEXPECTED_GENESIS',
        'NON_MONOTONIC_CLOCK', 'BAD_SIGNATURE', 'BAD_BODY_DIGEST', 'BAD_DIGEST', 'MALFORMED',
        'FOREIGN_ENTRY', 'SALE_INCONSISTENT'))
);

CREATE UNIQUE INDEX sync_exception_one_open
    ON sync_exception (terminal_id, kind, sequence) WHERE resolved_at IS NULL;
CREATE INDEX sync_exception_by_tenant ON sync_exception (tenant_id, raised_at DESC);

ALTER TABLE chain_head      ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_entry   ENABLE ROW LEVEL SECURITY;
ALTER TABLE sync_exception  ENABLE ROW LEVEL SECURITY;
ALTER TABLE chain_head      FORCE ROW LEVEL SECURITY;
ALTER TABLE journal_entry   FORCE ROW LEVEL SECURITY;
ALTER TABLE sync_exception  FORCE ROW LEVEL SECURITY;

CREATE POLICY chain_head_tenant ON chain_head
    USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant());
CREATE POLICY journal_entry_tenant ON journal_entry
    USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant());
CREATE POLICY sync_exception_tenant ON sync_exception
    USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant());

GRANT SELECT, INSERT, UPDATE ON chain_head TO mara_app;
GRANT SELECT, INSERT ON journal_entry TO mara_app;
GRANT SELECT, INSERT, UPDATE ON sync_exception TO mara_app;
GRANT USAGE ON SEQUENCE sync_exception_id_seq TO mara_app;

-- The one cross-tenant read: core-service has to learn which terminals have new entries
-- without being told a tenant first. Returns identifiers and a counter, never a sale.
CREATE OR REPLACE FUNCTION list_chain_heads(p_after TEXT, p_limit INTEGER)
    RETURNS TABLE (terminal_id TEXT, tenant_id TEXT, last_sequence BIGINT)
    LANGUAGE sql
    SECURITY DEFINER
    STABLE
    SET search_path = pg_catalog, public
AS $$
    SELECT h.terminal_id, h.tenant_id, h.last_sequence
      FROM chain_head h
     WHERE h.last_sequence > 0 AND h.terminal_id > p_after
     ORDER BY h.terminal_id
     LIMIT least(greatest(p_limit, 1), 1000)
$$;

REVOKE ALL ON FUNCTION list_chain_heads(TEXT, INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION list_chain_heads(TEXT, INTEGER) TO mara_app;
