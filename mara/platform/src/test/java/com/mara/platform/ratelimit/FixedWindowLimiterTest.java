package com.mara.platform.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FixedWindowLimiterTest {

    private static final Instant T0 = Instant.parse("2026-10-03T10:00:00Z");

    @Test
    void allowsUpToTheLimitThenRefuses() {
        var l = new FixedWindowLimiter(3, Duration.ofMinutes(1), 100);
        assertTrue(l.tryAcquire("a", T0));
        assertTrue(l.tryAcquire("a", T0.plusSeconds(1)));
        assertTrue(l.tryAcquire("a", T0.plusSeconds(2)));
        assertFalse(l.tryAcquire("a", T0.plusSeconds(3)));
    }

    @Test
    void keysAreIndependent() {
        var l = new FixedWindowLimiter(1, Duration.ofMinutes(1), 100);
        assertTrue(l.tryAcquire("a", T0));
        assertFalse(l.tryAcquire("a", T0));
        assertTrue(l.tryAcquire("b", T0));
    }

    @Test
    void windowReopensAfterItElapses() {
        var l = new FixedWindowLimiter(1, Duration.ofMinutes(1), 100);
        assertTrue(l.tryAcquire("a", T0));
        assertFalse(l.tryAcquire("a", T0.plusSeconds(59)));
        assertTrue(l.tryAcquire("a", T0.plusSeconds(60)));
    }

    @Test
    void aClockThatMovesBackwardsDoesNotWedgeTheKey() {
        var l = new FixedWindowLimiter(1, Duration.ofMinutes(1), 100);
        assertTrue(l.tryAcquire("a", T0));
        assertTrue(l.tryAcquire("a", T0.minusSeconds(30)));
    }

    @Test
    void retryAfterCountsDownAndIsAtLeastOne() {
        var l = new FixedWindowLimiter(1, Duration.ofMinutes(1), 100);
        l.tryAcquire("a", T0);
        assertEquals(60, l.retryAfterSeconds("a", T0));
        assertEquals(1, l.retryAfterSeconds("a", T0.plusSeconds(60)));
        assertEquals(1, l.retryAfterSeconds("unknown", T0));
    }

    @Test
    void memoryIsBoundedAndAFloodOfNewKeysSharesOneBucket() {
        var l = new FixedWindowLimiter(2, Duration.ofMinutes(1), 10);
        for (int i = 0; i < 1000; i++) {
            l.tryAcquire("ip-" + i, T0);
        }
        assertTrue(l.size() <= 11, "table must not grow past maxKeys plus the overflow bucket: " + l.size());
        assertFalse(l.tryAcquire("another-new-ip", T0), "the shared overflow bucket is exhausted");
    }

    @Test
    void expiredWindowsAreReclaimedBeforeSpilling() {
        var l = new FixedWindowLimiter(1, Duration.ofMinutes(1), 5);
        for (int i = 0; i < 5; i++) {
            assertTrue(l.tryAcquire("old-" + i, T0));
        }
        assertTrue(l.tryAcquire("fresh", T0.plusSeconds(61)), "old windows are dropped to make room");
    }

    @Test
    void concurrentCallersNeverExceedTheLimit() throws Exception {
        var l = new FixedWindowLimiter(50, Duration.ofMinutes(1), 100);
        AtomicInteger allowed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        for (int i = 0; i < 400; i++) {
            tasks.add(pool.submit(() -> {
                if (l.tryAcquire("same", T0)) {
                    allowed.incrementAndGet();
                }
            }));
        }
        for (var f : tasks) {
            f.get();
        }
        pool.shutdown();
        assertEquals(50, allowed.get());
    }

    @Test
    void rejectsNonsenseConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new FixedWindowLimiter(0, Duration.ofMinutes(1), 10));
        assertThrows(IllegalArgumentException.class, () -> new FixedWindowLimiter(1, Duration.ZERO, 10));
        assertThrows(IllegalArgumentException.class, () -> new FixedWindowLimiter(1, Duration.ofMinutes(1), 0));
    }
}
