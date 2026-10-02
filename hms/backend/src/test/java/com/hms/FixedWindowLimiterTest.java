package com.hms;

import static org.assertj.core.api.Assertions.assertThat;

import com.hms.platform.security.FixedWindowLimiter;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FixedWindowLimiterTest {
    private final AtomicLong now = new AtomicLong(1_000_000L);

    @Test
    void refusesOverTheLimitThenAllowsAgainInTheNextWindow() {
        var l = new FixedWindowLimiter(2, 60_000L, now::get);
        assertThat(l.hit("a").allowed()).isTrue();
        assertThat(l.hit("a").allowed()).isTrue();
        var refused = l.hit("a");
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfterSeconds()).isBetween(1L, 60L);
        assertThat(l.hit("b").allowed()).isTrue();
        now.addAndGet(60_000L);
        assertThat(l.hit("a").allowed()).isTrue();
    }
}
