-- Shared fixed-window counters for the unauthenticated endpoints, so the limit holds across replicas.
-- No tenant column and no row-level security: the keys are client addresses, not tenant data.
CREATE TABLE rate_limit_hits (
    bucket       text   NOT NULL,
    client_key   text   NOT NULL,
    window_start bigint NOT NULL,
    hits         integer NOT NULL,
    PRIMARY KEY (bucket, client_key)
);
CREATE INDEX rate_limit_hits_window ON rate_limit_hits (window_start);
