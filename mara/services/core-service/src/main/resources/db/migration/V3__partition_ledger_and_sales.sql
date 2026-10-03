-- Hash-partition the three tables that grow with every sale: ledger_txn, posting and sale.
--
-- WHY HASH ON tenant_id, AND NOT MONTH
-- PostgreSQL requires every unique key on a partitioned table to contain the partition key.
-- Three guarantees here are per-tenant and must stay guarantees of the database, not of the
-- application: a fiscal number belongs to one sale in a tenant, a terminal sequence posts one
-- ledger transaction, and a ledger transaction is reversed once. Each of them already carries
-- tenant_id, so hashing on tenant_id keeps all three as unique indexes. A month key would have
-- forced them to include occurred_at and so stop being global guarantees. The cost is that
-- retention by month is not a DETACH; it would have to be a later, deliberate step.
--
-- WHAT IT BUYS: each partition's indexes and vacuum are 1/16 of the table, tenant-scoped
-- statements are pruned to one partition at execution time (the row-level-security predicate
-- compares tenant_id with a STABLE function, which PostgreSQL prunes on), and a partition can be
-- moved to its own tablespace or server later. WHAT IT DOES NOT: point lookups by key are not
-- faster. A tenant that dominates (more than ~1/16 of all rows) lands in one partition.
--
-- Applies on existing data: the rows are copied into the new tables, the old ones dropped, and
-- sequences keep counting from where they were.

-- A non-superuser owner is subject to FORCE ROW LEVEL SECURITY and would copy zero rows (and
-- then the DROP below would lose them). The old tables are about to be dropped, so un-force them.
ALTER TABLE ledger_txn NO FORCE ROW LEVEL SECURITY;
ALTER TABLE posting    NO FORCE ROW LEVEL SECURITY;
ALTER TABLE sale       NO FORCE ROW LEVEL SECURITY;
-- Adding posting's foreign key to account validates against account's rows as the owner, so
-- account must not be forced either while that runs. It is forced again at the end.
ALTER TABLE account    NO FORCE ROW LEVEL SECURITY;

-- Triggers are cloned onto partitions, where TG_TABLE_NAME is the partition's name: take the
-- table's name as an argument so messages and lookups name the logical table.
CREATE OR REPLACE FUNCTION refuse_change() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only (% refused); correct it with a contra-entry',
            coalesce(TG_ARGV[0], TG_TABLE_NAME), TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END
$$;

CREATE OR REPLACE FUNCTION assert_txn_balanced() RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    v_txn   BIGINT;
    v_diff  BIGINT;
    v_count BIGINT;
BEGIN
    IF TG_ARGV[0] = 'ledger_txn' THEN
        v_txn := NEW.id;
    ELSE
        v_txn := NEW.txn_id;
    END IF;
    -- tenant_id in the predicate prunes this to one partition of posting.
    SELECT coalesce(sum(debit_minor), 0) - coalesce(sum(credit_minor), 0), count(*)
      INTO v_diff, v_count
      FROM posting WHERE tenant_id = NEW.tenant_id AND txn_id = v_txn;
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

-- ------------------------------------------------------------- new, empty tables

-- The sequences must outlive the old tables that own them.
ALTER SEQUENCE ledger_txn_id_seq OWNED BY NONE;
ALTER SEQUENCE posting_id_seq OWNED BY NONE;

CREATE TABLE ledger_txn_p (LIKE ledger_txn INCLUDING DEFAULTS) PARTITION BY HASH (tenant_id);
CREATE TABLE posting_p    (LIKE posting    INCLUDING DEFAULTS) PARTITION BY HASH (tenant_id);
CREATE TABLE sale_p       (LIKE sale       INCLUDING DEFAULTS) PARTITION BY HASH (tenant_id);

DO $$
DECLARE
    t TEXT;
    i INT;
BEGIN
    FOREACH t IN ARRAY ARRAY['ledger_txn', 'posting', 'sale']
    LOOP
        FOR i IN 0..15 LOOP
            EXECUTE format('CREATE TABLE %I PARTITION OF %I FOR VALUES WITH (MODULUS 16, REMAINDER %s)',
                           t || '_h' || lpad(i::text, 2, '0'), t || '_p', i);
        END LOOP;
    END LOOP;
END
$$;

-- ------------------------------------------------------------------- copy

INSERT INTO ledger_txn_p SELECT * FROM ledger_txn;
INSERT INTO posting_p    SELECT * FROM posting;
INSERT INTO sale_p       SELECT * FROM sale;

DROP TABLE posting, sale, ledger_txn;

ALTER TABLE ledger_txn_p RENAME TO ledger_txn;
ALTER TABLE posting_p    RENAME TO posting;
ALTER TABLE sale_p       RENAME TO sale;

ALTER SEQUENCE ledger_txn_id_seq OWNED BY ledger_txn.id;
ALTER SEQUENCE posting_id_seq OWNED BY posting.id;

-- ------------------------------------------------- keys, checks, foreign keys

ALTER TABLE ledger_txn
    ADD CONSTRAINT ledger_txn_pkey PRIMARY KEY (tenant_id, id),
    ADD CONSTRAINT ledger_txn_tenant_id_currency_key UNIQUE (tenant_id, id, currency),
    ADD CONSTRAINT ledger_txn_kind_known CHECK (kind IN ('SALE', 'REVERSAL')),
    ADD CONSTRAINT ledger_txn_source_pair CHECK ((source_terminal IS NULL) = (source_sequence IS NULL)),
    ADD CONSTRAINT ledger_txn_currency_upper CHECK (currency = upper(currency)),
    ADD CONSTRAINT ledger_txn_reversal_names_original CHECK ((kind = 'REVERSAL') = (reverses IS NOT NULL)),
    ADD CONSTRAINT ledger_txn_reverses_fkey FOREIGN KEY (tenant_id, reverses) REFERENCES ledger_txn (tenant_id, id);

-- Idempotency, per tenant: the same terminal sequence can never be posted twice, however many
-- times the feed is replayed or two pollers race. A terminal belongs to one tenant, so adding
-- tenant_id to the key changes nothing but makes it legal on a partitioned table.
CREATE UNIQUE INDEX ledger_txn_one_per_source ON ledger_txn (tenant_id, source_terminal, source_sequence)
    WHERE source_terminal IS NOT NULL;
CREATE UNIQUE INDEX ledger_txn_one_reversal ON ledger_txn (tenant_id, reverses) WHERE reverses IS NOT NULL;

ALTER TABLE posting
    ADD CONSTRAINT posting_pkey PRIMARY KEY (tenant_id, id),
    ADD CONSTRAINT posting_txn_fkey FOREIGN KEY (tenant_id, txn_id, currency)
        REFERENCES ledger_txn (tenant_id, id, currency),
    ADD CONSTRAINT posting_account_fkey FOREIGN KEY (tenant_id, account_code) REFERENCES account (tenant_id, code),
    -- A posting is a debit or a credit, never both and never nothing, and never negative:
    -- reversing is a contra-entry on the other side, not a minus sign.
    ADD CONSTRAINT posting_one_side CHECK (
        debit_minor >= 0 AND credit_minor >= 0 AND ((debit_minor > 0) <> (credit_minor > 0)));

CREATE INDEX posting_by_txn ON posting (tenant_id, txn_id);
CREATE INDEX posting_by_account ON posting (tenant_id, account_code);

ALTER TABLE sale
    ADD CONSTRAINT sale_pkey PRIMARY KEY (tenant_id, terminal_id, sequence),
    ADD CONSTRAINT sale_fiscal_status_known CHECK (fiscal_status IN ('NUMBERED', 'FISCAL_PENDING')),
    ADD CONSTRAINT sale_txn_fkey FOREIGN KEY (tenant_id, txn_id) REFERENCES ledger_txn (tenant_id, id);

-- A fiscal number may stand for one sale in a tenant. A second claim is not rejected (the till
-- did sell) but is stored unaccepted and raised, so only the first holds the number.
CREATE UNIQUE INDEX sale_fiscal_number_once ON sale (tenant_id, fiscal_number) WHERE fiscal_accepted;
CREATE INDEX sale_by_tenant_time ON sale (tenant_id, occurred_at);

-- ---------------------------------------------------------------- triggers

CREATE TRIGGER ledger_txn_append_only BEFORE UPDATE OR DELETE ON ledger_txn
    FOR EACH ROW EXECUTE FUNCTION refuse_change('ledger_txn');
CREATE TRIGGER posting_append_only BEFORE UPDATE OR DELETE ON posting
    FOR EACH ROW EXECUTE FUNCTION refuse_change('posting');
CREATE TRIGGER sale_append_only BEFORE UPDATE OR DELETE ON sale
    FOR EACH ROW EXECUTE FUNCTION refuse_change('sale');

-- TRUNCATE on a partition does not fire its parent's statement trigger, so every partition
-- carries its own (and the parent keeps one, for TRUNCATE of the whole table).
DO $$
DECLARE
    t TEXT;
    i INT;
BEGIN
    FOREACH t IN ARRAY ARRAY['ledger_txn', 'posting', 'sale']
    LOOP
        EXECUTE format('CREATE TRIGGER %I BEFORE TRUNCATE ON %I FOR EACH STATEMENT EXECUTE FUNCTION refuse_change(%L)',
                       t || '_no_truncate', t, t);
        FOR i IN 0..15 LOOP
            EXECUTE format('CREATE TRIGGER %I BEFORE TRUNCATE ON %I FOR EACH STATEMENT EXECUTE FUNCTION refuse_change(%L)',
                           t || '_h' || lpad(i::text, 2, '0') || '_no_truncate',
                           t || '_h' || lpad(i::text, 2, '0'), t);
        END LOOP;
    END LOOP;
END
$$;

CREATE CONSTRAINT TRIGGER ledger_txn_balanced AFTER INSERT ON ledger_txn
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION assert_txn_balanced('ledger_txn');
CREATE CONSTRAINT TRIGGER posting_balanced AFTER INSERT ON posting
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION assert_txn_balanced('posting');

-- ------------------------------------------------------------- RLS and grants

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['ledger_txn', 'posting', 'sale']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY %I ON %I USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant())',
            t || '_tenant', t);
    END LOOP;
END
$$;

ALTER TABLE account FORCE ROW LEVEL SECURITY;

-- Granted on the parents only. mara_app has no privilege on a partition, so it cannot name one
-- and step around the parent's row-level security.
GRANT SELECT, INSERT ON ledger_txn, posting, sale TO mara_app;
