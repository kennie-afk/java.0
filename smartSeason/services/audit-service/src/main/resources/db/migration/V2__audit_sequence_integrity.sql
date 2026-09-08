-- Makes the audit chain safe under concurrent writers.
--
-- The append path reads the highest sequence for the tenant and then inserts
-- the next one. With two writers that read is stale for one of them, and both
-- claim the same number: measured at 20 concurrent appends producing 9 distinct
-- sequences, which breaks verification outright.
--
-- The unique index is the guarantee. The service also takes a per-tenant
-- advisory lock so writers queue instead of colliding and retrying, but even if
-- that were removed the database would still refuse a duplicate.
DELETE FROM audit_records a
      USING audit_records b
      WHERE a.tenant_id = b.tenant_id
        AND a.sequence = b.sequence
        AND a.ctid > b.ctid;

CREATE UNIQUE INDEX uq_audit_records_tenant_sequence
    ON audit_records (tenant_id, sequence);
