package com.mara.platform.ratelimit;

import java.time.Instant;

/**
 * Something that counts requests per key and says yes or no. The in-memory
 * {@link FixedWindowLimiter} is one; a shared counter across replicas is another (see
 * service-kit). Callers need only this.
 */
public interface RateLimiter {

    /** {@code allowed} false means refuse with 429; {@code retryAfterSeconds} is at least 1 then. */
    record Decision(boolean allowed, long retryAfterSeconds) {
        public static final Decision ALLOWED = new Decision(true, 0);
    }

    Decision acquire(String key, Instant now);
}
