-- Links a worker to the user account they sign in with.
--
-- Without this there is no way to answer "which worker is this?" for the person
-- holding the phone, so "my tasks" and every other own-records-only rule had to
-- be enforced in the browser, which is no enforcement at all.
--
-- Nullable on purpose: a casual worker who never signs in is still a worker.
ALTER TABLE workers ADD COLUMN user_id UUID;

CREATE INDEX idx_workers_user_id ON workers (user_id);

-- One account cannot be two workers in the same tenant.
CREATE UNIQUE INDEX uq_workers_tenant_user
    ON workers (tenant_id, user_id)
    WHERE user_id IS NOT NULL;
