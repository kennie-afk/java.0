-- Bumped when the password is reset; a token carries the version it was issued under.
ALTER TABLE users ADD COLUMN token_version integer NOT NULL DEFAULT 0;
