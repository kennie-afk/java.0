package com.mara.kit.ratelimit;

import com.mara.platform.ratelimit.RateLimiter;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A fixed-window counter shared by every replica, so N replicas enforce one limit instead of N.
 *
 * <p>The count and its expiry are one atomic Lua step: the first hit in a window sets the
 * expiry, so a crashed caller cannot leave a counter that never resets.
 *
 * <p><b>Failure policy: degrade to the local limiter, never open, never closed.</b> If Redis
 * errors or times out, this replica keeps limiting on its own in-memory counter (the behaviour
 * before Redis existed) and skips Redis for {@code retryRedisAfter} so a dead Redis is not hit
 * on every request. Failing open would let an outage of Redis become an unthrottled flood;
 * failing closed would refuse every terminal's uploads because a cache is down, and a till
 * must not be punished for that. Cost of the policy: during an outage the effective ceiling
 * returns to N times the limit.
 */
public final class RedisFixedWindowLimiter implements RateLimiter, AutoCloseable {

    private static final String SCRIPT = """
            local c = redis.call('INCR', KEYS[1])
            if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); ttl = tonumber(ARGV[1]) end
            return {c, ttl}
            """;

    private final String name;
    private final int limit;
    private final long windowMillis;
    private final RateLimiter local;
    private final long retryRedisAfterMillis;
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final AtomicLong redisDownUntilMillis = new AtomicLong();
    private final AtomicLong fallbackCount = new AtomicLong();

    public RedisFixedWindowLimiter(
            String name, String redisUrl, int limit, Duration window, RateLimiter local,
            Duration commandTimeout, Duration retryRedisAfter) {
        if (limit < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("limit and window must be positive");
        }
        this.name = name;
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.local = local;
        this.retryRedisAfterMillis = retryRedisAfter.toMillis();
        RedisURI uri = RedisURI.create(redisUrl);
        uri.setTimeout(commandTimeout);
        this.client = RedisClient.create(uri);
        StatefulRedisConnection<String, String> c = null;
        try {
            c = client.connect();
            c.setTimeout(commandTimeout);
        } catch (RuntimeException e) {
            // Redis is down at start: begin on the local limiter and keep trying later.
            redisDownUntilMillis.set(System.currentTimeMillis() + this.retryRedisAfterMillis);
        }
        this.connection = c;
    }

    @Override
    public Decision acquire(String key, Instant now) {
        long t = System.currentTimeMillis();
        if (t >= redisDownUntilMillis.get()) {
            try {
                StatefulRedisConnection<String, String> c = connection != null ? connection : client.connect();
                RedisCommands<String, String> cmd = c.sync();
                List<Object> r = cmd.eval(SCRIPT, ScriptOutputType.MULTI,
                        new String[] {"mara:rl:" + name + ":" + key}, Long.toString(windowMillis));
                long count = (Long) r.get(0);
                long ttlMillis = (Long) r.get(1);
                if (count <= limit) {
                    return Decision.ALLOWED;
                }
                return new Decision(false, Math.max(1, (ttlMillis + 999) / 1000));
            } catch (RuntimeException e) {
                redisDownUntilMillis.set(t + retryRedisAfterMillis);
            }
        }
        fallbackCount.incrementAndGet();
        return local.acquire(key, now);
    }

    /** How many decisions were made by the local limiter because Redis was unavailable. */
    public long fallbackDecisions() {
        return fallbackCount.get();
    }

    @Override
    public void close() {
        if (connection != null) {
            connection.close();
        }
        client.shutdown();
    }
}
