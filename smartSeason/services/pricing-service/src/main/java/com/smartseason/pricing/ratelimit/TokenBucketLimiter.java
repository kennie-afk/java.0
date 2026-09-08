package com.smartseason.pricing.ratelimit;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class TokenBucketLimiter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketLimiter.class);

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> script;

    public TokenBucketLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new org.springframework.core.io.ClassPathResource("scripts/token-bucket.lua"));
        this.script.setResultType(List.class);
    }

    public RateLimitDecision take(String key, int capacity, double refillPerSecond) {
        try {
            List<?> result = redis.execute(
                    script,
                    List.of(key),
                    Integer.toString(capacity),
                    Double.toString(refillPerSecond),
                    Long.toString(Instant.now().toEpochMilli()),
                    "1");

            if (result == null || result.size() < 3) {
                return RateLimitDecision.unlimited();
            }
            long allowed = ((Number) result.get(0)).longValue();
            long remaining = ((Number) result.get(1)).longValue();
            long retryAfter = ((Number) result.get(2)).longValue();
            return new RateLimitDecision(allowed == 1, remaining, retryAfter);
        } catch (RuntimeException ex) {

            log.warn("Rate limiter unavailable, allowing the request: {}", ex.getMessage());
            return RateLimitDecision.unlimited();
        }
    }
}
