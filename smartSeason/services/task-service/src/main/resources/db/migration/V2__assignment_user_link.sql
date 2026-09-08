-- Records which signed-in account an assignment belongs to.
--
-- The worker id alone is not enough to enforce "only your own tasks": task-service
-- has no way to turn the caller's user id into a worker id without asking
-- workforce-service on every request. Carrying the user id on the assignment lets
-- the check happen locally, in the service that owns the row.
ALTER TABLE task_assignments ADD COLUMN worker_user_id UUID;

CREATE INDEX idx_task_assignments_worker_user_id ON task_assignments (worker_user_id);
