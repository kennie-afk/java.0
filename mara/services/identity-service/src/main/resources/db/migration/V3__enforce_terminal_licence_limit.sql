-- The terminal-count limit was checked only in application code: resolve_enrolment
-- reports active_terminals against licensed_terminals, read once at the start of the
-- enrolment transaction, before anything is written. Two different enrolment codes for
-- the same tenant, redeemed at the same time by two different transactions, each read
-- that count before either had inserted its terminal, so both could see room and both
-- proceed — leaving the tenant with more active terminals than it is licensed for.
--
-- That is the same shape of gap the row-level security migration exists to close for
-- tenant isolation, applied here to a numeric invariant instead of a boundary: the
-- application's own check is the first line, and this is the line that holds when two
-- requests race past it at once.
--
-- A trigger, not a CHECK constraint, because a CHECK constraint is evaluated against a
-- single row and cannot see the rest of the table. Enforcing a cross-row count needs a
-- statement that runs on write, can see every other row, and can lock against them.

CREATE OR REPLACE FUNCTION enforce_terminal_licence_limit() RETURNS TRIGGER
    LANGUAGE plpgsql
    -- Same reasoning as current_tenant(): this runs on every insert, so it must not be
    -- resolvable to an attacker-controlled function of the same name.
    SET search_path = pg_catalog, public
AS $$
DECLARE
    licensed INTEGER;
    occupied INTEGER;
BEGIN
    -- Locks the tenant row for the rest of this transaction. A concurrent insert for
    -- the same tenant blocks here until this transaction commits or rolls back, so by
    -- the time this statement's count runs, no other enrolment for this tenant is still
    -- in flight — either it has already committed and is counted below, or it is
    -- waiting behind this lock and will count this row when its own turn comes.
    SELECT licensed_terminals INTO licensed
      FROM tenant
     WHERE id = NEW.tenant_id
       FOR UPDATE;

    SELECT count(*) INTO occupied
      FROM terminal
     WHERE tenant_id = NEW.tenant_id
       AND status IN ('PENDING', 'ACTIVE');

    IF occupied >= licensed THEN
        RAISE EXCEPTION 'tenant % is licensed for % terminal(s) and already has %',
            NEW.tenant_id, licensed, occupied
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER terminal_licence_limit
    BEFORE INSERT ON terminal
    FOR EACH ROW
    EXECUTE FUNCTION enforce_terminal_licence_limit();

COMMENT ON FUNCTION enforce_terminal_licence_limit() IS
    'Serialises concurrent enrolments for one tenant against its licensed_terminals cap. '
    'The application checks this before inserting; this is the line that holds when two '
    'enrolments for different codes race past that check at the same moment, because '
    'neither transaction could see the other''s still-uncommitted insert.';
