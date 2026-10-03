-- Hash-partition journal_entry, the table that gets one wide row (the whole signed sale) for
-- every sale every terminal makes.
--
-- WHY HASH ON terminal_id, AND NOT MONTH OR TENANT
-- Every read and write of this table is for one terminal: the primary key is (terminal_id,
-- sequence) and a terminal's chain is appended under its chain_head lock. Hashing on terminal_id
-- keeps that primary key a legal unique key on a partitioned table (it contains the partition
-- key), spreads a large tenant's terminals across all partitions instead of piling them into
-- one, and lets a terminal's whole chain live in one partition. A month key would have needed
-- received_at inside the primary key, and (terminal_id, sequence) would stop being unique in
-- the database. Tenant-wide reads (the back-office listing) visit every partition's tenant
-- index: measured in docs/CAPACITY-RESULTS.md.

ALTER TABLE journal_entry NO FORCE ROW LEVEL SECURITY;   -- see core V3: a non-superuser owner would copy 0 rows

CREATE TABLE journal_entry_p (LIKE journal_entry INCLUDING DEFAULTS) PARTITION BY HASH (terminal_id);

DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 0..15 LOOP
        EXECUTE format('CREATE TABLE %I PARTITION OF journal_entry_p FOR VALUES WITH (MODULUS 16, REMAINDER %s)',
                       'journal_entry_h' || lpad(i::text, 2, '0'), i);
    END LOOP;
END
$$;

INSERT INTO journal_entry_p SELECT * FROM journal_entry;
DROP TABLE journal_entry;
ALTER TABLE journal_entry_p RENAME TO journal_entry;

ALTER TABLE journal_entry
    ADD CONSTRAINT journal_entry_pkey PRIMARY KEY (terminal_id, sequence),
    ADD CONSTRAINT journal_entry_sequence_positive CHECK (sequence >= 1),
    ADD CONSTRAINT journal_entry_digest_sizes CHECK (
        octet_length(body_digest) = 32 AND octet_length(previous_digest) = 32 AND octet_length(digest) = 32);

CREATE INDEX journal_entry_by_tenant_time ON journal_entry (tenant_id, epoch_second);

CREATE TRIGGER journal_entry_no_update_delete
    BEFORE UPDATE OR DELETE ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION journal_entry_immutable();
CREATE TRIGGER journal_entry_no_truncate
    BEFORE TRUNCATE ON journal_entry
    FOR EACH STATEMENT EXECUTE FUNCTION journal_entry_immutable();

-- TRUNCATE on a partition does not fire the parent's statement trigger: guard every partition.
DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 0..15 LOOP
        EXECUTE format('CREATE TRIGGER %I BEFORE TRUNCATE ON %I FOR EACH STATEMENT EXECUTE FUNCTION journal_entry_immutable()',
                       'journal_entry_h' || lpad(i::text, 2, '0') || '_no_truncate',
                       'journal_entry_h' || lpad(i::text, 2, '0'));
    END LOOP;
END
$$;

ALTER TABLE journal_entry ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_entry FORCE ROW LEVEL SECURITY;
CREATE POLICY journal_entry_tenant ON journal_entry
    USING (tenant_id = current_tenant()) WITH CHECK (tenant_id = current_tenant());

-- Parent only: mara_app cannot name a partition and step around the parent's policy.
GRANT SELECT, INSERT ON journal_entry TO mara_app;
