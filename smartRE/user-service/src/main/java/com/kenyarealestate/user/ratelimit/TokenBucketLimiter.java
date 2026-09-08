package com.kenyarealestate.user.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A Redis-backed token bucket shared across every replica of this service.
 *
 * <p><b>Fails open.</b> If Redis is unreachable the request is allowed. That is a
 * deliberate trade: this limiter is defence in depth behind the gateway, and a cache
 * outage taking the whole service offline would be a worse failure than briefly serving
 * unthrottled. The failure is logged at warn so it is visible rather than silent.
 */
@Slf4j
@Component
public class TokenBucketLimiter {

    private final StringRedisTemplate redis;
    private final RedisScript<List> script;

    public TokenBucketLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        DefaultRedisScript<List> s = new DefaultRedisScript<>();
        s.setLocation(new ClassPathResource("scripts/token-bucket.lua"));
        s.setResultType(List.class);
        this.script = s;
    }

    public RateLimitDecision tryConsume(String key, int capacity, int refillPerMinute) {
        if (capacity <= 0 || refillPerMinute <= 0) return RateLimitDecision.unlimited();
        try {
            @SuppressWarnings("unchecked")
            List<Long> result = redis.execute(script, List.of(key),
                    String.valueOf(capacity),
                    String.valueOf(refillPerMinute / 60.0),
                    String.valueOf(System.currentTimeMillis()),
                    "1");
            if (result == null || result.size() < 3) {
                log.warn("Rate limiter got an unusable reply for key={}; allowing the request", key);
                return RateLimitDecision.unlimited();
            }
            return new RateLimitDecision(result.get(0) == 1L, result.get(1), result.get(2));
        } catch (Exception e) {
            log.warn("Rate limiter could not reach Redis ({}); allowing the request for key={}",
                    e.getMessage(), key);
            return RateLimitDecision.unlimited();
        }
    }
}
