-- V2 creates user_audit_log with one column set (user_id, event_type, ...); V8 declares
-- the table the code actually maps (target_user_id, action, ...) as CREATE TABLE IF NOT
-- EXISTS, so on an empty database V8 silently skips the create and then fails building an
-- index on target_user_id, which V2's table does not have. Databases that predate the
-- collision already hold V8's shape and never hit it.
--
-- Both migrations are applied history and frozen (their checksums live in every database
-- that has run them), so the fix cannot be an edit to either. This callback runs before
-- each migration and removes the stale V2-shaped table only in the one state where it is
-- in the way: it exists, lacks target_user_id, and V8 has not yet been applied.
-- V2's table is never written to by any code, so it holds nothing worth keeping.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'user_audit_log')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'user_audit_log'
                  AND column_name = 'target_user_id')
       AND NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '8' AND success) THEN
        DROP TABLE user_audit_log;
    END IF;
END $$;
