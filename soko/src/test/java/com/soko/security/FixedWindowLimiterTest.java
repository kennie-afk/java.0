package com.soko.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FixedWindowLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private FixedWindowLimiter limiter(int limit) {
        return new FixedWindowLimiter(limit, 60_000L, now::get);
    }

    @Test
    void allowsUpToTheLimitThenRefusesWithAWaitTime() {
        FixedWindowLimiter l = limiter(3);
        assertThat(l.hit("a").allowed()).isTrue();
        assertThat(l.hit("a").allowed()).isTrue();
        assertThat(l.hit("a").allowed()).isTrue();
        FixedWindowLimiter.Decision refused = l.hit("a");
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void countsEachKeySeparately() {
        FixedWindowLimiter l = limiter(1);
        assertThat(l.hit("a").allowed()).isTrue();
        assertThat(l.hit("a").allowed()).isFalse();
        assertThat(l.hit("b").allowed()).isTrue();
    }

    @Test
    void startsAFreshWindowOnceTheOldOneExpires() {
        FixedWindowLimiter l = limiter(1);
        assertThat(l.hit("a").allowed()).isTrue();
        assertThat(l.hit("a").allowed()).isFalse();
        now.addAndGet(60_000L);
        assertThat(l.hit("a").allowed()).isTrue();
    }

    @Test
    void reportsTheTimeLeftInTheWindow() {
        FixedWindowLimiter l = limiter(1);
        l.hit("a");
        now.addAndGet(45_000L);
        assertThat(l.hit("a").retryAfterSeconds()).isEqualTo(15L);
    }

    @Test
    void rejectsANonsenseConfiguration() {
        assertThatThrownBy(() -> new FixedWindowLimiter(0, 1000L)).isInstanceOf(IllegalArgumentException.class);
    }
}
