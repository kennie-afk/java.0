package com.smartseason.telemetryingest.platform;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rolls raw telemetry up into hourly buckets and then removes it.
 *
 * <p>{@code telemetry_readings} was the one table in the platform that could never be
 * recovered from. Every device reading was kept for ever and nothing pruned it: the only
 * {@code @Scheduled} work anywhere was the outbox relays. At the volumes this platform is
 * meant to reach that is not a performance problem, it is a terminal one — autovacuum falls
 * behind, backups stop fitting in their window, and index maintenance becomes the longest
 * operation in the system. It fails late, all at once, and on the largest customer first.
 *
 * <p>The design was already implied by the schema. {@code downsampled_readings} existed with
 * {@code bucket_start}, {@code bucket_minutes}, {@code avg/min/max} and {@code sample_count}
 * — everything needed to keep the shape of history without the rows. Nothing ever wrote to
 * it. This is that intent, finished: summarise, then discard the detail.
 *
 * <h2>Why the aggregate is written before the delete</h2>
 * Both statements run in one transaction, aggregate first. If anything fails, the raw rows
 * are still there and the job retries; the alternative ordering can delete readings whose
 * summary was never committed, which is data loss dressed up as a cleanup.
 *
 * <h2>Why an advisory lock</h2>
 * This service autoscales. Two replicas running the same roll-up would race, and while the
 * unique index makes the aggregate itself idempotent, both would still scan and delete the
 * same range and fight over the same pages. {@code pg_try_advisory_lock} makes exactly one
 * replica do the work and the others return immediately — the same mechanism the audit
 * chain uses for the same reason.
 *
 * <h2>Why it deletes in batches</h2>
 * One statement removing a month of readings takes a long transaction, a large amount of
 * WAL and locks that a live ingest path is queued behind. Batches keep each transaction
 * short enough that ingestion never notices.
 */
@Component
public class TelemetryRetention {

    private static final Logger log = LoggerFactory.getLogger(TelemetryRetention.class);

    /** Arbitrary but fixed: the key identifies this job, and must never collide with another. */
    private static final long LOCK_KEY = 4_732_115_009L;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    @Value("${smartseason.telemetry.retention.enabled:true}")
    private boolean enabled;

    /** How long raw readings are kept. Summaries are kept indefinitely; they are small. */
    @Value("${smartseason.telemetry.retention.raw-days:30}")
    private int rawDays;

    /** Rows removed per transaction. Small enough that ingest never queues behind it. */
    @Value("${smartseason.telemetry.retention.batch-size:5000}")
    private int batchSize;

    /** Bound on one run, so a large backlog is worked off over several runs, not one long one. */
    @Value("${smartseason.telemetry.retention.max-batches:20}")
    private int maxBatches;

    public TelemetryRetention(DataSource dataSource, PlatformTransactionManager transactionManager) {
        this.jdbc = new JdbcTemplate(dataSource);
        // A TransactionTemplate rather than @Transactional on runBatch. Spring's annotation
        // works through a proxy, and run() calls runBatch() on `this`, so the proxy is
        // bypassed and no transaction would ever start. That failure is silent and it would
        // take the advisory lock with it: pg_try_advisory_xact_lock is released at the end
        // of *its* transaction, so with no transaction the lock is dropped immediately and
        // every replica would proceed at once - exactly what the lock exists to prevent.
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "${smartseason.telemetry.retention.cron:0 20 * * * *}")
    public void run() {
        if (!enabled) {
            return;
        }

        Instant cutoff = Instant.now().minus(rawDays, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        long summarised = 0;
        long deleted = 0;

        for (int batch = 0; batch < maxBatches; batch++) {
            Result result = runBatch(cutoff);
            if (result == null) {
                // Another replica holds the lock. Nothing to report and nothing to retry;
                // it is doing the same work.
                return;
            }
            summarised += result.summarised();
            deleted += result.deleted();
            if (result.deleted() == 0) {
                break;
            }
        }

        if (deleted > 0) {
            log.info("Telemetry retention: {} raw readings older than {} removed, {} bucket rows written",
                    deleted, cutoff, summarised);
        }
    }

    private Result runBatch(Instant cutoff) {
        return transactions.execute(status -> batchWork(cutoff));
    }

    private Result batchWork(Instant cutoff) {
        Boolean acquired = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, LOCK_KEY);
        if (!Boolean.TRUE.equals(acquired)) {
            return null;
        }

        // The batch is defined by the ids being removed, and the aggregate is computed from
        // exactly those rows. Selecting the ids first means the summary and the delete can
        // never disagree about which readings they covered.
        var ids = jdbc.queryForList(
                "SELECT id FROM telemetry_readings WHERE recorded_at < ? ORDER BY recorded_at LIMIT ?",
                java.util.UUID.class, java.sql.Timestamp.from(cutoff), batchSize);

        if (ids.isEmpty()) {
            return new Result(0, 0);
        }

        Object[] idArray = ids.toArray();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));

        int summarised = jdbc.update("""
                INSERT INTO downsampled_readings
                    (id, tenant_id, created_at, updated_at, version,
                     device_id, metric, bucket_start, bucket_minutes,
                     avg_value, min_value, max_value, sample_count)
                SELECT gen_random_uuid(), r.tenant_id, NOW(), NOW(), 0,
                       r.device_id, r.metric,
                       date_trunc('hour', r.recorded_at), 60,
                       AVG(r.value), MIN(r.value), MAX(r.value), COUNT(*)
                  FROM telemetry_readings r
                 WHERE r.id IN (%s)
                 GROUP BY r.tenant_id, r.device_id, r.metric, date_trunc('hour', r.recorded_at)
                ON CONFLICT (tenant_id, device_id, metric, bucket_start, bucket_minutes)
                DO UPDATE SET
                    -- A bucket can be written by more than one batch when an hour straddles a
                    -- batch boundary. Combining as a weighted mean keeps the average correct
                    -- rather than letting the last batch overwrite the earlier one.
                    avg_value = (downsampled_readings.avg_value * downsampled_readings.sample_count
                                 + EXCLUDED.avg_value * EXCLUDED.sample_count)
                                / (downsampled_readings.sample_count + EXCLUDED.sample_count),
                    min_value = LEAST(downsampled_readings.min_value, EXCLUDED.min_value),
                    max_value = GREATEST(downsampled_readings.max_value, EXCLUDED.max_value),
                    sample_count = downsampled_readings.sample_count + EXCLUDED.sample_count,
                    updated_at = NOW()
                """.formatted(placeholders), idArray);

        int deleted = jdbc.update(
                "DELETE FROM telemetry_readings WHERE id IN (%s)".formatted(placeholders), idArray);

        return new Result(summarised, deleted);
    }

    private record Result(int summarised, int deleted) {}
}
