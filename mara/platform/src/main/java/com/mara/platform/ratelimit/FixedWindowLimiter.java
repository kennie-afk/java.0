package com.mara.platform.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A fixed-window request counter per key. Pure: the clock is an argument, so behaviour at a
 * window boundary can be tested without sleeping.
 *
 * <p>Per process, not shared: with N replicas the effective ceiling is N times the limit. That
 * is the honest trade for having no gateway or Redis in the path of the till; it still turns an
 * unbounded flood into a bounded one on every replica.
 *
 * <p>Memory is bounded. Expired windows are dropped when the table is full; if it is still full
 * (a flood of distinct keys) new keys share one overflow bucket, so an attacker rotating
 * addresses degrades into one noisy bucket instead of exhausting the heap.
 */
public final class FixedWindowLimiter {

    private static final String OVERFLOW = "\u0000overflow";

    private record Window(long startMillis, int count) {
    }

    private final int limit;
    private final long windowMillis;
    private final int maxKeys;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowLimiter(int limit, Duration window, int maxKeys) {
        if (limit < 1 || window.isZero() || window.isNegative() || maxKeys < 1) {
            throw new IllegalArgumentException("limit, window and maxKeys must be positive");
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.maxKeys = maxKeys;
    }

    /** Counts one request for {@code key}; false means it is over the limit and should be refused. */
    public boolean tryAcquire(String key, Instant now) {
        long t = now.toEpochMilli();
        String k = windows.containsKey(key) || windows.size() < maxKeys ? key : spill(t, key);
        boolean[] allowed = {false};
        windows.compute(k, (name, w) -> {
            if (w == null || t - w.startMillis() >= windowMillis || t < w.startMillis()) {
                allowed[0] = true;
                return new Window(t, 1);
            }
            if (w.count() >= limit) {
                return w;
            }
            allowed[0] = true;
            return new Window(w.startMillis(), w.count() + 1);
        });
        return allowed[0];
    }

    /** Seconds until the window for {@code key} reopens (at least 1), for a Retry-After header. */
    public long retryAfterSeconds(String key, Instant now) {
        Window w = windows.get(windows.containsKey(key) ? key : OVERFLOW);
        if (w == null) {
            return 1;
        }
        long remaining = w.startMillis() + windowMillis - now.toEpochMilli();
        return Math.max(1, (remaining + 999) / 1000);
    }

    int size() {
        return windows.size();
    }

    private String spill(long t, String key) {
        for (Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator(); it.hasNext(); ) {
            if (t - it.next().getValue().startMillis() >= windowMillis) {
                it.remove();
            }
        }
        return windows.size() < maxKeys ? key : OVERFLOW;
    }
}
