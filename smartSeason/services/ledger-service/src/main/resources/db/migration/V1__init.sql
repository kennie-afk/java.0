CREATE TABLE accounts (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    account_code           VARCHAR(255) NOT NULL UNIQUE,
    name                   VARCHAR(255) NOT NULL,
    account_type           VARCHAR(64) NOT NULL,
    owner_org_id           UUID,
    owner_user_id          UUID,
    currency               VARCHAR(255) NOT NULL,
    normal_balance         VARCHAR(64) NOT NULL,
    status                 VARCHAR(64) NOT NULL,
    parent_account_id      UUID
);
CREATE INDEX ix_accounts_tenant ON accounts (tenant_id);
CREATE INDEX ix_accounts_owner_org_id ON accounts (owner_org_id);
CREATE INDEX ix_accounts_owner_user_id ON accounts (owner_user_id);

CREATE TABLE journal_entries (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    entry_number           VARCHAR(255) NOT NULL UNIQUE,
    description            VARCHAR(255) NOT NULL,
    source_event           VARCHAR(255),
    source_ref             VARCHAR(255),
    posted_at              TIMESTAMPTZ NOT NULL,
    effective_date         DATE NOT NULL,
    currency               VARCHAR(255) NOT NULL,
    total_debit            NUMERIC(18,4) NOT NULL,
    total_credit           NUMERIC(18,4) NOT NULL,
    balanced               BOOLEAN NOT NULL,
    reversal_of_id         UUID,
    idempotency_key        VARCHAR(255) NOT NULL UNIQUE
);
CREATE INDEX ix_journal_entries_tenant ON journal_entries (tenant_id);
CREATE INDEX ix_journal_entries_source_ref ON journal_entries (source_ref);

CREATE TABLE postings (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    journal_entry_id       UUID NOT NULL,
    account_id             UUID NOT NULL,
    account_code           VARCHAR(255) NOT NULL,
    direction              VARCHAR(64) NOT NULL,
    amount                 NUMERIC(18,4) NOT NULL,
    currency               VARCHAR(255) NOT NULL,
    posted_at              TIMESTAMPTZ NOT NULL,
    memo                   VARCHAR(255)
);
CREATE INDEX ix_postings_tenant ON postings (tenant_id);
CREATE INDEX ix_postings_journal_entry_id ON postings (journal_entry_id);
CREATE INDEX ix_postings_account_id ON postings (account_id);

CREATE TABLE account_balances (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    account_id             UUID NOT NULL UNIQUE,
    account_code           VARCHAR(255) NOT NULL,
    currency               VARCHAR(255) NOT NULL,
    debit_total            NUMERIC(18,4) NOT NULL,
    credit_total           NUMERIC(18,4) NOT NULL,
    balance                NUMERIC(18,4) NOT NULL,
    posting_count          BIGINT NOT NULL,
    last_posted_at         TIMESTAMPTZ
);
CREATE INDEX ix_account_balances_tenant ON account_balances (tenant_id);
CREATE INDEX ix_account_balances_account_code ON account_balances (account_code);
