-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_accounts_keyset
    ON accounts (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_journal_entries_keyset
    ON journal_entries (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_postings_keyset
    ON postings (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_account_balances_keyset
    ON account_balances (tenant_id, created_at DESC, id DESC);
