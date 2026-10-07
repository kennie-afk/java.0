package com.soko.security;

import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A fixed-window counter kept in Postgres, so every replica counts the same hits. One atomic
 * upsert per request: it starts a new window when the old one has run out, otherwise adds one,
 * and returns the resulting count. If the database cannot be reached the limiter falls back to a
 * per-process counter rather than refusing everyone: the database being down already fails the
 * requests it guards, and an unbounded retry storm is what the fallback still bounds.
 */
public final class SharedWindowLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SharedWindowLimiter.class);

    private static final String HIT = """
            INSERT INTO rate_limit_hits (bucket, client_key, window_start, hits)
            VALUES (?, ?, ?, 1)
            ON CONFLICT (bucket, client_key) DO UPDATE SET
                hits = CASE WHEN rate_limit_hits.window_start + ? <= EXCLUDED.window_start
                            THEN 1 ELSE rate_limit_hits.hits + 1 END,
                window_start = CASE WHEN rate_limit_hits.window_start + ? <= EXCLUDED.window_start
                            THEN EXCLUDED.window_start ELSE rate_limit_hits.window_start END
            RETURNING window_start, hits
            """;

    private final JdbcTemplate jdbc;
    private final String bucket;
    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;
    private final FixedWindowLimiter fallback;
    private long lastPrune;

    public SharedWindowLimiter(JdbcTemplate jdbc, String bucket, int limit, long windowMillis, LongSupplier clock) {
        this.jdbc = jdbc;
        this.bucket = bucket;
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clock = clock;
        this.fallback = new FixedWindowLimiter(limit, windowMillis, clock);
        this.lastPrune = clock.getAsLong();
    }

    @Override
    public Decision hit(String key) {
        long now = clock.getAsLong();
        try {
            long[] row = jdbc.queryForObject(HIT,
                    (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)},
                    bucket, key, now, windowMillis, windowMillis);
            prune(now);
            if (row[1] <= limit) {
                return new Decision(true, 0);
            }
            long remaining = windowMillis - (now - row[0]);
            return new Decision(false, Math.max(1, (remaining + 999) / 1000));
        } catch (RuntimeException unreachable) {
            log.warn("shared rate limiter unavailable, using the per-process counter: {}", unreachable.toString());
            return fallback.hit(key);
        }
    }

    /** Removes this bucket's expired windows once per window so the table stays small. */
    private void prune(long now) {
        synchronized (this) {
            if (now - lastPrune < windowMillis) {
                return;
            }
            lastPrune = now;
        }
        try {
            jdbc.update("DELETE FROM rate_limit_hits WHERE bucket = ? AND window_start + ? <= ?",
                    bucket, windowMillis, now);
        } catch (RuntimeException ignored) {
            // retried at the next window
        }
    }
}
