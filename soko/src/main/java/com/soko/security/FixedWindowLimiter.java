package com.soko.security;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Counts hits per key in fixed windows. In memory and per replica: enough to stop one client
 * hammering a login form, not a global quota. A shared limiter (Redis) would be the next step if
 * the API is scaled out and an attacker spreads requests across replicas.
 */
public final class FixedWindowLimiter {

    /** The answer to one {@link #hit}: whether it is allowed, and how long until the window resets. */
    public record Decision(boolean allowed, long retryAfterSeconds) {}

    private record Window(long startedAt, int count) {}

    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private long lastSweep;

    public FixedWindowLimiter(int limit, long windowMillis, LongSupplier clock) {
        if (limit < 1 || windowMillis < 1) {
            throw new IllegalArgumentException("limit and window must be positive");
        }
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clock = clock;
        this.lastSweep = clock.getAsLong();
    }

    public FixedWindowLimiter(int limit, long windowMillis) {
        this(limit, windowMillis, System::currentTimeMillis);
    }

    public Decision hit(String key) {
        long now = clock.getAsLong();
        sweep(now);
        Window updated =
                windows.compute(
                        key,
                        (k, w) ->
                                (w == null || now - w.startedAt() >= windowMillis)
                                        ? new Window(now, 1)
                                        : new Window(w.startedAt(), w.count() + 1));
        if (updated.count() <= limit) {
            return new Decision(true, 0);
        }
        long remaining = windowMillis - (now - updated.startedAt());
        return new Decision(false, Math.max(1, (remaining + 999) / 1000));
    }

    /** Drops expired windows so a stream of one-off clients cannot grow the map without bound. */
    private synchronized void sweep(long now) {
        if (now - lastSweep < windowMillis) {
            return;
        }
        lastSweep = now;
        for (Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator(); it.hasNext(); ) {
            if (now - it.next().getValue().startedAt() >= windowMillis) {
                it.remove();
            }
        }
    }
}
