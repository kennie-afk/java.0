package com.mara.kit.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.mara.platform.ratelimit.FixedWindowLimiter;
import com.mara.platform.ratelimit.RateLimiter;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Two "replicas" (two limiter instances) against one Redis must enforce ONE limit. The Redis
 * tests need {@code -Dmara.test.redis.url=redis://localhost:6379}; the outage test needs nothing.
 */
class RedisFixedWindowLimiterTest {

    private static final String REDIS = System.getProperty("mara.test.redis.url");

    private static RedisFixedWindowLimiter replica(String name, String url, int limit) {
        return new RedisFixedWindowLimiter(name, url, limit, Duration.ofSeconds(30),
                new FixedWindowLimiter(limit, Duration.ofSeconds(30), 100),
                Duration.ofMillis(200), Duration.ofSeconds(5));
    }

    @Test
    void tworeplicasShareOneCounter() {
        Assumptions.assumeTrue(REDIS != null && !REDIS.isBlank(), "needs -Dmara.test.redis.url");
        String name = "t" + UUID.randomUUID();
        try (var a = replica(name, REDIS, 10); var b = replica(name, REDIS, 10)) {
            int allowed = 0;
            for (int i = 0; i < 20; i++) {
                if ((i % 2 == 0 ? a : b).acquire("1.2.3.4", Instant.now()).allowed()) {
                    allowed++;
                }
            }
            assertThat(allowed).as("10 allowed in total across both replicas, not 10 each").isEqualTo(10);
            assertThat(a.fallbackDecisions() + b.fallbackDecisions()).isZero();
            RateLimiter.Decision refused = a.acquire("1.2.3.4", Instant.now());
            assertThat(refused.allowed()).isFalse();
            assertThat(refused.retryAfterSeconds()).isBetween(1L, 30L);
            // another address has its own window
            assertThat(b.acquire("5.6.7.8", Instant.now()).allowed()).isTrue();
        }
    }

    @Test
    void concurrentCallersNeverExceedTheLimit() {
        Assumptions.assumeTrue(REDIS != null && !REDIS.isBlank(), "needs -Dmara.test.redis.url");
        String name = "c" + UUID.randomUUID();
        try (var a = replica(name, REDIS, 50); var b = replica(name, REDIS, 50)) {
            AtomicInteger allowed = new AtomicInteger();
            IntStream.range(0, 400).parallel().forEach(i -> {
                if ((i % 2 == 0 ? a : b).acquire("9.9.9.9", Instant.now()).allowed()) {
                    allowed.incrementAndGet();
                }
            });
            assertThat(allowed.get()).isEqualTo(50);
        }
    }

    @Test
    void whenRedisIsUnreachableTheLocalLimiterStillLimitsNeitherOpenNorClosed() {
        // Nothing listens on this port.
        try (var limiter = replica("down", "redis://127.0.0.1:1", 3)) {
            int allowed = 0;
            for (int i = 0; i < 10; i++) {
                if (limiter.acquire("1.1.1.1", Instant.now()).allowed()) {
                    allowed++;
                }
            }
            assertThat(allowed).as("not open: the local limiter still caps at 3").isEqualTo(3);
            assertThat(allowed).as("not closed: the first requests still pass").isPositive();
            assertThat(limiter.fallbackDecisions()).isEqualTo(10);
        }
    }

    @Test
    void theFactoryGivesAnInMemoryLimiterWhenNoRedisIsConfigured() {
        RateLimiter limiter = RateLimiters.create("x", "", 2, Duration.ofMinutes(1));
        assertThat(limiter).isInstanceOf(FixedWindowLimiter.class);
        assertThat(limiter.acquire("k", Instant.now()).allowed()).isTrue();
        assertThat(limiter.acquire("k", Instant.now()).allowed()).isTrue();
        assertThat(limiter.acquire("k", Instant.now()).allowed()).isFalse();
    }
}
