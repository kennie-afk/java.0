-- Mara identity-service — the trust root.
--
-- Two roles matter here and they are not the same:
--   mara_owner  runs migrations, owns the tables, may bypass row-level security
--   mara_app    the application connects as this, and CANNOT bypass RLS
--
-- The separation is the point. If the application connected as the owner, every
-- policy below would be advisory, because a table owner bypasses RLS by default.

-- ---------------------------------------------------------------- tenancy ---

CREATE TABLE tenant (
    id                  TEXT PRIMARY KEY,
    legal_name          TEXT        NOT NULL,
    trading_name        TEXT        NOT NULL,
    country_code        CHAR(2)     NOT NULL,
    default_currency    CHAR(3)     NOT NULL,
    tax_identifier      TEXT,                       -- KRA PIN, VAT number, per jurisdiction
    licensed_terminals  INTEGER     NOT NULL DEFAULT 1,
    status              TEXT        NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT tenant_status_known     CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT tenant_licence_positive CHECK (licensed_terminals > 0),
    CONSTRAINT tenant_country_upper    CHECK (country_code = upper(country_code)),
    CONSTRAINT tenant_currency_upper   CHECK (default_currency = upper(default_currency))
);

CREATE TABLE branch (
    id            TEXT PRIMARY KEY,
    tenant_id     TEXT        NOT NULL REFERENCES tenant (id),
    name          TEXT        NOT NULL,
    timezone      TEXT        NOT NULL,
    status        TEXT        NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT branch_status_known CHECK (status IN ('ACTIVE', 'CLOSED'))
);

CREATE INDEX branch_by_tenant ON branch (tenant_id) WHERE status = 'ACTIVE';

-- ----------------------------------------------------------------- staff ---

CREATE TABLE staff (
    id             TEXT PRIMARY KEY,
    tenant_id      TEXT        NOT NULL REFERENCES tenant (id),
    branch_id      TEXT        REFERENCES branch (id),
    display_name   TEXT        NOT NULL,
    role           TEXT        NOT NULL,
    -- Argon2id, never a bare hash. A four-digit PIN has 10k possibilities, so the
    -- only thing standing between a stolen table and every cashier's PIN is how
    -- expensive each guess is.
    pin_hash       TEXT        NOT NULL,
    failed_attempts SMALLINT   NOT NULL DEFAULT 0,
    locked_until   TIMESTAMPTZ,
    status         TEXT        NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT staff_role_known   CHECK (role IN ('CASHIER', 'SUPERVISOR', 'MANAGER', 'OWNER')),
    CONSTRAINT staff_status_known CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT staff_attempts_sane CHECK (failed_attempts >= 0),
    -- A cashier belongs to exactly one branch; an owner may span all of them.
    CONSTRAINT staff_branch_required_below_owner
        CHECK (role = 'OWNER' OR branch_id IS NOT NULL)
);

CREATE INDEX staff_by_branch ON staff (tenant_id, branch_id) WHERE status = 'ACTIVE';

-- -------------------------------------------------------------- terminals ---

CREATE TABLE terminal (
    id               TEXT PRIMARY KEY,
    tenant_id        TEXT        NOT NULL REFERENCES tenant (id),
    branch_id        TEXT        NOT NULL REFERENCES branch (id),
    label            TEXT        NOT NULL,          -- "Lane 4", "Front desk"
    -- X.509 SubjectPublicKeyInfo, base64. The matching private key was generated on
    -- the device and has never left it.
    public_key       TEXT        NOT NULL,
    status           TEXT        NOT NULL DEFAULT 'PENDING',
    enrolled_at      TIMESTAMPTZ,
    last_seen_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT terminal_status_known
        CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT terminal_enrolled_has_timestamp
        CHECK (status = 'PENDING' OR enrolled_at IS NOT NULL)
);

-- One key, one terminal, globally. A cloned device presenting an already-registered
-- key is refused by the database even if the application check is skipped.
CREATE UNIQUE INDEX terminal_public_key_unique ON terminal (public_key);

CREATE INDEX terminal_by_branch ON terminal (tenant_id, branch_id) WHERE status = 'ACTIVE';

-- ------------------------------------------------------------- enrolment ---

CREATE TABLE enrolment_code (
    id            TEXT PRIMARY KEY,
    tenant_id     TEXT        NOT NULL REFERENCES tenant (id),
    branch_id     TEXT        NOT NULL REFERENCES branch (id),
    -- SHA-256 over a domain separator and the normalised code. Not salted per tenant:
    -- the device presenting a code has no tenant yet — resolving the code is HOW it
    -- learns which tenant it belongs to — so a tenant-salted hash could never be looked
    -- up. What stands in for the salt is the code's own scarcity: 49 bits of entropy,
    -- single use, dead after fifteen minutes.
    code_hash     BYTEA       NOT NULL,
    issued_by     TEXT        NOT NULL REFERENCES staff (id),
    issued_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    redeemed_at   TIMESTAMPTZ,
    -- Deferred on purpose. Redemption is the serialisation point and has to happen
    -- BEFORE the terminal row is written: if it ran after, two devices could both
    -- create a terminal and only then discover one of them lost the code. Deferring
    -- the check to commit lets the safe ordering stand while still guaranteeing that
    -- a committed redemption points at a terminal that exists.
    redeemed_by_terminal TEXT REFERENCES terminal (id) DEFERRABLE INITIALLY DEFERRED,

    CONSTRAINT enrolment_expiry_after_issue CHECK (expires_at > issued_at),
    CONSTRAINT enrolment_redemption_consistent
        CHECK ((redeemed_at IS NULL) = (redeemed_by_terminal IS NULL))
);

-- Globally unique, not per tenant: a code must resolve to exactly one tenant, or
-- resolution before the tenant is known would be ambiguous.
CREATE UNIQUE INDEX enrolment_code_hash_unique ON enrolment_code (code_hash);

-- Only unredeemed codes are worth looking up, and this is the lookup the enrolment
-- endpoint performs on every attempt.
CREATE INDEX enrolment_code_open ON enrolment_code (code_hash)
    WHERE redeemed_at IS NULL;
