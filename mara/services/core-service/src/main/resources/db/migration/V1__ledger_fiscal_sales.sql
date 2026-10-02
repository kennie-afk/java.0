-- core-service: the ledger, fiscal number leases and the sales posted from verified journals.
--
-- Three things must be true together or not at all: a sale, its tenders, and its ledger
-- postings. They share this database so that is a BEGIN..COMMIT and not a saga.

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

CREATE OR REPLACE FUNCTION refuse_change() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only (% refused); correct it with a contra-entry', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END
$$;

-- ------------------------------------------------------------------- ledger

CREATE TABLE account (
    tenant_id TEXT NOT NULL,
    code      TEXT NOT NULL,
    name      TEXT NOT NULL,
    kind      TEXT NOT NULL,
    PRIMARY KEY (tenant_id, code),
    CONSTRAINT account_kind_known CHECK (kind IN ('ASSET', 'LIABILITY', 'INCOME', 'SUSPENSE'))
);

CREATE TABLE ledger_txn (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       TEXT        NOT NULL,
    kind            TEXT        NOT NULL,
    currency        CHAR(3)     NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_terminal TEXT,
    source_sequence BIGINT,
    reverses        BIGINT REFERENCES ledger_txn (id),
    memo            TEXT,

    CONSTRAINT ledger_txn_kind_known CHECK (kind IN ('SALE', 'REVERSAL')),
    CONSTRAINT ledger_txn_source_pair CHECK ((source_terminal IS NULL) = (source_sequence IS NULL)),
    CONSTRAINT ledger_txn_currency_upper CHECK (currency = upper(currency)),
    CONSTRAINT ledger_txn_reversal_names_original CHECK ((kind = 'REVERSAL') = (reverses IS NOT NULL)),
    UNIQUE (id, currency)
);

-- Idempotency: the same terminal sequence can never be posted twice, however many times the
-- feed is replayed or two pollers race.
CREATE UNIQUE INDEX ledger_txn_one_per_source ON ledger_txn (source_terminal, source_sequence)
    WHERE source_terminal IS NOT NULL;
CREATE UNIQUE INDEX ledger_txn_one_reversal ON ledger_txn (reverses) WHERE reverses IS NOT NULL;

CREATE TABLE posting (
    id           BIGSERIAL PRIMARY KEY,
    txn_id       BIGINT  NOT NULL,
    tenant_id    TEXT    NOT NULL,
    account_code TEXT    NOT NULL,
    currency     CHAR(3) NOT NULL,
    debit_minor  BIGINT  NOT NULL DEFAULT 0,
    credit_minor BIGINT  NOT NULL DEFAULT 0,

    FOREIGN KEY (txn_id, currency) REFERENCES ledger_txn (id, currency),
    FOREIGN KEY (tenant_id, account_code) REFERENCES account (tenant_id, code),
    -- A posting is a debit or a credit, never both and never nothing, and never negative:
    -- reversing is a contra-entry on the other side, not a minus sign.
    CONSTRAINT posting_one_side CHECK (
        debit_minor >= 0 AND credit_minor >= 0 AND ((debit_minor > 0) <> (credit_minor > 0)))
);

CREATE INDEX posting_by_txn ON posting (txn_id);
CREATE INDEX posting_by_account ON posting (tenant_id, account_code);

CREATE TRIGGER ledger_txn_append_only BEFORE UPDATE OR DELETE ON ledger_txn
    FOR EACH ROW EXECUTE FUNCTION refuse_change();
CREATE TRIGGER posting_append_only BEFORE UPDATE OR DELETE ON posting
    FOR EACH ROW EXECUTE FUNCTION refuse_change();
CREATE TRIGGER ledger_txn_no_truncate BEFORE TRUNCATE ON ledger_txn
    FOR EACH STATEMENT EXECUTE FUNCTION refuse_change();
CREATE TRIGGER posting_no_truncate BEFORE TRUNCATE ON posting
    FOR EACH STATEMENT EXECUTE FUNCTION refuse_change();

-- Debits equal credits per transaction, asserted in the database and not merely in code.
-- Deferred to commit so a transaction's postings can be inserted one at a time; it fires per
-- posting row and per transaction row, so a transaction with no postings is refused too.
CREATE OR REPLACE FUNCTION assert_txn_balanced() RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    v_txn   BIGINT;
    v_diff  BIGINT;
    v_count BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'ledger_txn' THEN
        v_txn := NEW.id;
    ELSE
        v_txn := NEW.txn_id;
    END IF;
    SELECT coalesce(sum(debit_minor), 0) - coalesce(sum(credit_minor), 0), count(*)
      INTO v_diff, v_count
      FROM posting WHERE txn_id = v_txn;
    IF v_count < 2 THEN
        RAISE EXCEPTION 'ledger transaction % has % postings; at least two are required', v_txn, v_count
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF v_diff <> 0 THEN
        RAISE EXCEPTION 'ledger transaction % is unbalanced by %', v_txn, v_diff
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NULL;
END
$$;

CREATE CONSTRAINT TRIGGER ledger_txn_balanced AFTER INSERT ON ledger_txn
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION assert_txn_balanced();
CREATE CONSTRAINT TRIGGER posting_balanced AFTER INSERT ON posting
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION assert_txn_balanced();

-- -------------------------------------------------------------------- sales

CREATE TABLE sale (
    terminal_id     TEXT        NOT NULL,
    sequence        BIGINT      NOT NULL,
    tenant_id       TEXT        NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    currency        CHAR(3)     NOT NULL,
    total_minor     BIGINT      NOT NULL,
    net_minor       BIGINT      NOT NULL,
    tax_minor       BIGINT      NOT NULL,
    applied_minor   BIGINT      NOT NULL,
    fiscal_status   TEXT        NOT NULL,
    fiscal_number   BIGINT,
    fiscal_accepted BOOLEAN     NOT NULL DEFAULT false,
    cashier_staff_id TEXT,
    consistent      BOOLEAN     NOT NULL,
    txn_id          BIGINT REFERENCES ledger_txn (id),
    body            JSONB       NOT NULL,
    ingested_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (terminal_id, sequence),
    CONSTRAINT sale_fiscal_status_known CHECK (fiscal_status IN ('NUMBERED', 'FISCAL_PENDING'))
);

-- A fiscal number may stand for one sale in a tenant. A second claim is not rejected (the till
-- did sell) but is stored unaccepted and raised, so only the first holds the number.
CREATE UNIQUE INDEX sale_fiscal_number_once ON sale (tenant_id, fiscal_number) WHERE fiscal_accepted;
CREATE INDEX sale_by_tenant_time ON sale (tenant_id, occurred_at);

CREATE TRIGGER sale_append_only BEFORE UPDATE OR DELETE ON sale
    FOR EACH ROW EXECUTE FUNCTION refuse_change();
CREATE TRIGGER sale_no_truncate BEFORE TRUNCATE ON sale
    FOR EACH STATEMENT EXECUTE FUNCTION refuse_change();

CREATE TABLE ingest_cursor (
    terminal_id   TEXT PRIMARY KEY,
    tenant_id     TEXT   NOT NULL,
    last_sequence BIGINT NOT NULL DEFAULT 0
);

-- ------------------------------------------------------------------- fiscal

CREATE TABLE fiscal_series (
    tenant_id   TEXT PRIMARY KEY,
    next_number BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT fiscal_series_positive CHECK (next_number >= 1)
);

CREATE TABLE fiscal_lease (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    TEXT        NOT NULL,
    terminal_id  TEXT        NOT NULL,
    first_number BIGINT      NOT NULL,
    last_number  BIGINT      NOT NULL,
    issued_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    returned_at  TIMESTAMPTZ,
    CONSTRAINT fiscal_lease_forward CHECK (last_number >= first_number),
    CONSTRAINT fiscal_lease_expiry CHECK (expires_at > issued_at),
    UNIQUE (tenant_id, first_number)
);

CREATE INDEX fiscal_lease_by_terminal ON fiscal_lease (terminal_id, issued_at DESC);

-- Leases are disjoint by construction (one counter per tenant, taken under a row lock) and
-- also by assertion: a second path to a lease cannot overlap an existing one.
CREATE OR REPLACE FUNCTION assert_lease_disjoint() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM fiscal_lease l
                WHERE l.tenant_id = NEW.tenant_id
                  AND l.first_number <= NEW.last_number AND NEW.first_number <= l.last_number) THEN
        RAISE EXCEPTION 'fiscal lease %..% overlaps an existing lease', NEW.first_number, NEW.last_number
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER fiscal_lease_disjoint BEFORE INSERT ON fiscal_lease
    FOR EACH ROW EXECUTE FUNCTION assert_lease_disjoint();

-- Unused numbers handed back are voided, never recycled: a reissued fiscal number is worse
-- than a missing one.
CREATE TABLE fiscal_void (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   TEXT        NOT NULL,
    lease_id    BIGINT      NOT NULL REFERENCES fiscal_lease (id),
    from_number BIGINT      NOT NULL,
    to_number   BIGINT      NOT NULL,
    reason      TEXT        NOT NULL,
    voided_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fiscal_void_forward CHECK (to_number >= from_number)
);

CREATE TRIGGER fiscal_void_append_only BEFORE UPDATE OR DELETE ON fiscal_void
    FOR EACH ROW EXECUTE FUNCTION refuse_change();

-- ---------------------------------------------------------------- exceptions

CREATE TABLE core_exception (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   TEXT        NOT NULL,
    terminal_id TEXT        NOT NULL,
    sequence    BIGINT      NOT NULL DEFAULT 0,
    kind        TEXT        NOT NULL,
    detail      TEXT        NOT NULL,
    raised_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT core_exception_kind_known CHECK (kind IN (
        'FISCAL_DUPLICATE', 'FISCAL_OUT_OF_LEASE', 'SALE_UNBALANCED', 'SALE_UNPOSTABLE'))
);

CREATE UNIQUE INDEX core_exception_one_open ON core_exception (terminal_id, kind, sequence) WHERE resolved_at IS NULL;

-- ----------------------------------------------------------------------- RLS

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['account', 'ledger_txn', 'posting', 'sale', 'ingest_cursor', 'fiscal_series',
                             'fiscal_lease', 'fiscal_void', 'core_exception']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY %I ON %I USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant())',
            t || '_tenant', t);
    END LOOP;
END
$$;

GRANT SELECT, INSERT ON account, ledger_txn, posting, sale, fiscal_void TO mara_app;
GRANT SELECT, INSERT, UPDATE ON ingest_cursor, fiscal_series, fiscal_lease, core_exception TO mara_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO mara_app;
