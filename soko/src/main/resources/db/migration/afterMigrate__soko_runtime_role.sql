-- Runs after every migrate (Flyway callback), so a changed password or system key in the
-- environment takes effect on the next start. The values come from the environment through
-- Flyway placeholders (spring.flyway.placeholders.*); they are checked for unsafe characters
-- by the application before Flyway runs, because a placeholder is substituted as text.
ALTER ROLE soko_app WITH LOGIN PASSWORD '${soko_app_password}';

INSERT INTO soko_system_key (only_row, key_hash)
VALUES (true, sha256(convert_to('${soko_system_key}', 'UTF8')))
ON CONFLICT (only_row) DO UPDATE SET key_hash = EXCLUDED.key_hash;

REVOKE ALL ON soko_system_key FROM soko_app;
REVOKE ALL ON flyway_schema_history FROM soko_app;
