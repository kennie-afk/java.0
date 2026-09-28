-- Makes telemetry retention possible, and makes the roll-up safe to repeat.
--
-- telemetry_readings is the highest-volume table in the platform and nothing has ever
-- removed a row from it. Its indexes were on tenant_id, device_id, plot_id and metric --
-- none on the time column -- so "delete everything older than X" was a sequential scan of
-- the whole table, which is why it was never going to be done casually.
--
-- BRIN rather than btree on recorded_at: readings arrive in roughly time order, so the
-- physical layout already correlates with the value. BRIN stores a summary per block range
-- instead of an entry per row, which for an append-only table of this size is a few hundred
-- kilobytes against several gigabytes, and range scans are what retention does.
CREATE INDEX IF NOT EXISTS ix_telemetry_readings_recorded_brin
    ON telemetry_readings USING BRIN (recorded_at);

-- The roll-up must be safe to run twice. Without this, a job that is retried after a
-- partial failure -- or two replicas racing before the advisory lock was added -- would
-- silently double-count a bucket, and an averaged metric that is quietly wrong is worse
-- than one that is missing.
CREATE UNIQUE INDEX IF NOT EXISTS uq_downsampled_readings_bucket
    ON downsampled_readings (tenant_id, device_id, metric, bucket_start, bucket_minutes);

-- Reading the history back is a time-range query per device, which neither existing index
-- serves.
CREATE INDEX IF NOT EXISTS ix_downsampled_readings_device_bucket
    ON downsampled_readings (tenant_id, device_id, bucket_start DESC);
