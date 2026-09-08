-- Tenant isolation enforced below the application.
--
-- The application already filters by tenant in every query. This exists for the day
-- it does not — a new endpoint, a hand-written report, a JOIN that lost its predicate
-- in review. Application filtering is the first line; this is the line that holds when
-- the first one is wrong, and it is the difference between a bug and a breach.
--
-- The tenant travels in a session GUC rather than a connection parameter, because
-- PgBouncer in transaction pooling mode hands the same physical connection to
-- different requests. set_config(..., true) scopes it to the transaction, so it
-- cannot leak into whatever runs next on that connection.

-- ------------------------------------------------------------------ roles ---

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mara_app') THEN
        CREATE ROLE mara_app NOLOGIN;
    END IF;
END
$$;

GRANT SELECT, INSERT, UPDATE ON tenant, branch, staff, terminal, enrolment_code TO mara_app;

-- Deliberately no DELETE anywhere in this service. Terminals and staff are revoked by
-- status, never removed, because the sales they authorised must stay attributable to
-- something. A row that can be deleted is a row that can be denied.

-- --------------------------------------------------------- current tenant ---

CREATE OR REPLACE FUNCTION current_tenant() RETURNS TEXT
    LANGUAGE sql
    STABLE
    -- Empty search_path: this runs inside every policy check, so it must not be
    -- resolvable to an attacker-controlled function of the same name.
    SET search_path = pg_catalog
AS $$
    SELECT nullif(current_setting('mara.tenant_id', true), '')
$$;

COMMENT ON FUNCTION current_tenant() IS
    'Tenant for the current transaction, set by the application from a signed gateway claim. '
    'NULL when unset, which every policy below treats as "match nothing".';

-- --------------------------------------------------------------- policies ---

ALTER TABLE tenant         ENABLE ROW LEVEL SECURITY;
ALTER TABLE branch         ENABLE ROW LEVEL SECURITY;
ALTER TABLE staff          ENABLE ROW LEVEL SECURITY;
ALTER TABLE terminal       ENABLE ROW LEVEL SECURITY;
ALTER TABLE enrolment_code ENABLE ROW LEVEL SECURITY;

-- FORCE applies the policies to the table owner too. Without it the owner bypasses
-- them silently, and a migration or a maintenance script becomes a hole.
ALTER TABLE tenant         FORCE ROW LEVEL SECURITY;
ALTER TABLE branch         FORCE ROW LEVEL SECURITY;
ALTER TABLE staff          FORCE ROW LEVEL SECURITY;
ALTER TABLE terminal       FORCE ROW LEVEL SECURITY;
ALTER TABLE enrolment_code FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_self_only ON tenant
    USING (id = current_tenant())
    WITH CHECK (id = current_tenant());

-- USING governs what is visible; WITH CHECK governs what may be written. Both are
-- required: without WITH CHECK a tenant could INSERT a row belonging to another and
-- then be unable to see the thing it just created.
CREATE POLICY branch_tenant_isolation ON branch
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

CREATE POLICY staff_tenant_isolation ON staff
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

CREATE POLICY terminal_tenant_isolation ON terminal
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

CREATE POLICY enrolment_tenant_isolation ON enrolment_code
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

-- ------------------------------------------------------------- enrolment ---

-- Enrolment must resolve a code before a tenant context exists — the device presenting
-- it is not yet anybody. That lookup is the one operation that has to cross the tenant
-- boundary, so it is a SECURITY DEFINER function with a fixed signature rather than a
-- policy exemption: it can answer exactly one question and reveals nothing else.
CREATE OR REPLACE FUNCTION resolve_enrolment(p_code_hash BYTEA)
    RETURNS TABLE (
        tenant_id          TEXT,
        branch_id          TEXT,
        enrolment_id       TEXT,
        issued_at          TIMESTAMPTZ,
        expires_at         TIMESTAMPTZ,
        redeemed           BOOLEAN,
        active_terminals   INTEGER,
        licensed_terminals INTEGER
    )
    LANGUAGE sql
    SECURITY DEFINER
    STABLE
    SET search_path = pg_catalog, public
AS $$
    SELECT e.tenant_id,
           e.branch_id,
           e.id,
           e.issued_at,
           e.expires_at,
           e.redeemed_at IS NOT NULL,
           (SELECT count(*)::INTEGER FROM terminal t
             WHERE t.tenant_id = e.tenant_id AND t.status IN ('PENDING', 'ACTIVE')),
           t.licensed_terminals
      FROM enrolment_code e
      JOIN tenant t ON t.id = e.tenant_id
     WHERE e.code_hash = p_code_hash
       AND t.status = 'ACTIVE'
$$;

REVOKE ALL ON FUNCTION resolve_enrolment(BYTEA) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION resolve_enrolment(BYTEA) TO mara_app;

COMMENT ON FUNCTION resolve_enrolment(BYTEA) IS
    'Resolves an enrolment code hash across tenants. The ONLY cross-tenant read in this '
    'service. Takes a hash, never a plaintext code, so it cannot be used to enumerate '
    'codes without already knowing one.';

-- Redemption is a conditional UPDATE, not a read-then-write. Two devices presenting the
-- same code concurrently both reach this; exactly one updates a row, and the loser sees
-- zero rows affected and is rejected. No lock, no race.
CREATE OR REPLACE FUNCTION redeem_enrolment(
        p_enrolment_id TEXT,
        p_terminal_id  TEXT,
        p_now          TIMESTAMPTZ)
    RETURNS BOOLEAN
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
    WITH claimed AS (
        UPDATE enrolment_code
           SET redeemed_at = p_now,
               redeemed_by_terminal = p_terminal_id
         WHERE id = p_enrolment_id
           AND redeemed_at IS NULL
           AND expires_at > p_now
        RETURNING id
    )
    SELECT EXISTS (SELECT 1 FROM claimed)
$$;

REVOKE ALL ON FUNCTION redeem_enrolment(TEXT, TEXT, TIMESTAMPTZ) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION redeem_enrolment(TEXT, TEXT, TIMESTAMPTZ) TO mara_app;

COMMENT ON FUNCTION redeem_enrolment(TEXT, TEXT, TIMESTAMPTZ) IS
    'Single-use redemption as one atomic conditional UPDATE. Returns false if the code '
    'was already spent or has expired, so concurrent attempts cannot both succeed.';

-- ------------------------------------------------- cross-tenant key check ---

-- Whether a public key is already registered to ANY terminal, in any tenant.
--
-- This has to see across tenants, and that is the point. A cloned device presenting a
-- key already registered to a different shop is exactly the case worth catching, and a
-- tenant-scoped check would be blind to it — it would report "no such key" and happily
-- enrol the clone. Like resolve_enrolment, it answers one closed question: it takes a
-- key the caller already holds and returns a boolean, so it cannot be used to read or
-- enumerate anyone's terminals.
CREATE OR REPLACE FUNCTION terminal_key_exists(p_public_key TEXT)
    RETURNS BOOLEAN
    LANGUAGE sql
    SECURITY DEFINER
    STABLE
    SET search_path = pg_catalog, public
AS $$
    SELECT EXISTS (SELECT 1 FROM terminal WHERE public_key = p_public_key)
$$;

REVOKE ALL ON FUNCTION terminal_key_exists(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION terminal_key_exists(TEXT) TO mara_app;

COMMENT ON FUNCTION terminal_key_exists(TEXT) IS
    'Cross-tenant existence check for a terminal public key. Returns a boolean only, so '
    'it cannot enumerate terminals. Required because a cloned device may be presenting a '
    'key registered to a different tenant, which a scoped query could never see.';
