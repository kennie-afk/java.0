-- Telemetry retention as a database function.
--
-- The roll-up-then-delete job is about every tenant at once by design. With row-level security
-- the service's role sees no rows when no tenant is bound, so the job's SQL moves here, where
-- it runs as the schema owner. Behaviour is unchanged from the version that lived in Java:
-- one advisory lock so a single replica works at a time, the readings to remove chosen first,
-- the hourly summary written from exactly those readings, then exactly those readings deleted,
-- all in the caller's transaction so a failure leaves the raw rows in place.

CREATE OR REPLACE FUNCTION ss_telemetry_retention_batch(p_cutoff timestamptz, p_batch integer)
RETURNS TABLE (acquired boolean, summarised integer, deleted integer)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $fn$
DECLARE
    ids uuid[];
    n_summarised integer := 0;
    n_deleted integer := 0;
BEGIN
    IF NOT pg_try_advisory_xact_lock(4732115009) THEN
        RETURN QUERY SELECT false, 0, 0;
        RETURN;
    END IF;

    SELECT array_agg(s.id) INTO ids
      FROM (SELECT id FROM telemetry_readings
             WHERE recorded_at < p_cutoff
             ORDER BY recorded_at
             LIMIT p_batch) s;

    IF ids IS NULL THEN
        RETURN QUERY SELECT true, 0, 0;
        RETURN;
    END IF;

    INSERT INTO downsampled_readings
        (id, tenant_id, created_at, updated_at, version,
         device_id, metric, bucket_start, bucket_minutes,
         avg_value, min_value, max_value, sample_count)
    SELECT gen_random_uuid(), r.tenant_id, NOW(), NOW(), 0,
           r.device_id, r.metric,
           date_trunc('hour', r.recorded_at), 60,
           AVG(r.value), MIN(r.value), MAX(r.value), COUNT(*)
      FROM telemetry_readings r
     WHERE r.id = ANY(ids)
     GROUP BY r.tenant_id, r.device_id, r.metric, date_trunc('hour', r.recorded_at)
    ON CONFLICT (tenant_id, device_id, metric, bucket_start, bucket_minutes)
    DO UPDATE SET
        -- A bucket can be written by more than one batch when an hour straddles a batch
        -- boundary; a weighted mean keeps the average right instead of last-write-wins.
        avg_value = (downsampled_readings.avg_value * downsampled_readings.sample_count
                     + EXCLUDED.avg_value * EXCLUDED.sample_count)
                    / (downsampled_readings.sample_count + EXCLUDED.sample_count),
        min_value = LEAST(downsampled_readings.min_value, EXCLUDED.min_value),
        max_value = GREATEST(downsampled_readings.max_value, EXCLUDED.max_value),
        sample_count = downsampled_readings.sample_count + EXCLUDED.sample_count,
        updated_at = NOW();
    GET DIAGNOSTICS n_summarised = ROW_COUNT;

    DELETE FROM telemetry_readings WHERE id = ANY(ids);
    GET DIAGNOSTICS n_deleted = ROW_COUNT;

    RETURN QUERY SELECT true, n_summarised, n_deleted;
END
$fn$;

REVOKE ALL ON FUNCTION ss_telemetry_retention_batch(timestamptz, integer) FROM PUBLIC;
