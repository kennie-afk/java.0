-- A fiscal lease request is idempotent: the terminal names its request (a signed query
-- parameter) or, failing that, the request's own signature stands for it. Asking again with the
-- same key returns the same lease instead of taking another block of numbers, so a retry after a
-- lost response, or a replay of a captured request inside the 5-minute signature window, cannot
-- drain the tenant's number space.
ALTER TABLE fiscal_lease ADD COLUMN request_key TEXT;

CREATE UNIQUE INDEX fiscal_lease_one_per_request ON fiscal_lease (terminal_id, request_key)
    WHERE request_key IS NOT NULL;
