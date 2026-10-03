package com.mara.kit.ratelimit;

import com.mara.platform.ratelimit.FixedWindowLimiter;
import com.mara.platform.ratelimit.RateLimiter;
import java.time.Duration;

/**
 * Builds the limiter for one endpoint group: shared through Redis when {@code mara.redis.url}
 * (env {@code MARA_REDIS_URL}) is set, otherwise per process. Every limiter keeps an in-memory
 * one behind it for the Redis-outage case; see {@link RedisFixedWindowLimiter}.
 */
public final class RateLimiters {

    private RateLimiters() {
    }

    public static RateLimiter create(String name, String redisUrl, int limit, Duration window) {
        FixedWindowLimiter local = new FixedWindowLimiter(limit, window, 20_000);
        if (redisUrl == null || redisUrl.isBlank()) {
            return local;
        }
        return new RedisFixedWindowLimiter(
                name, redisUrl, limit, window, local, Duration.ofMillis(100), Duration.ofSeconds(5));
    }
}
