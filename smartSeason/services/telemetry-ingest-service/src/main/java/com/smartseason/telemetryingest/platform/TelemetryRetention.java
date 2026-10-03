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
        // The batch runs inside a database function (V5__telemetry_retention_function.sql), not
        // as SQL here. Retention is the one job that is deliberately about every tenant at once,
        // and row-level security - correctly - shows this service's role no rows when no tenant
        // is bound. The function runs as the schema owner, takes the advisory lock, rolls the
        // chosen readings up and deletes exactly those readings, all in this transaction.
        var row = jdbc.queryForMap("SELECT * FROM ss_telemetry_retention_batch(?, ?)",
                java.sql.Timestamp.from(cutoff), batchSize);
        if (!Boolean.TRUE.equals(row.get("acquired"))) {
            return null;
        }
        return new Result(((Number) row.get("summarised")).intValue(),
                ((Number) row.get("deleted")).intValue());
    }

    private record Result(int summarised, int deleted) {}
}
