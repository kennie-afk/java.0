-- Idempotency keys: a write sent with an Idempotency-Key is carried out once, however many times it is sent. A client that
-- queued the write while offline can resend it after a dropped connection without doubling it.
CREATE TABLE idempotency_keys (
  org_id uuid NOT NULL,
  actor_id uuid NOT NULL,
  key text NOT NULL CHECK (key ~ '^[A-Za-z0-9_-]{16,80}$'),
  method text NOT NULL,
  path text NOT NULL,
  request_hash bytea NOT NULL,
  state text NOT NULL DEFAULT 'IN_PROGRESS' CHECK (state IN ('IN_PROGRESS', 'DONE')),
  status int,
  content_type text,
  response_body bytea,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (org_id, actor_id, key)
);
CREATE INDEX idempotency_keys_age ON idempotency_keys (created_at);
ALTER TABLE idempotency_keys ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON idempotency_keys USING (org_id = current_org()) WITH CHECK (org_id = current_org());
