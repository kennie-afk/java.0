package com.mara.kit.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TerminalRateLimitFilterTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T10:00:00Z"), ZoneOffset.UTC);

    private int hit(TerminalRateLimitFilter f, String path, String addr, String forwardedFor, AtomicInteger reached)
            throws Exception {
        var req = new MockHttpServletRequest("POST", path);
        req.setRemoteAddr(addr);
        if (forwardedFor != null) {
            req.addHeader("X-Forwarded-For", forwardedFor);
        }
        var res = new MockHttpServletResponse();
        f.doFilter(req, res, (a, b) -> reached.incrementAndGet());
        return res.getStatus();
    }

    @Test
    void refusesAnAddressOverTheLimitWithRetryAfter() throws Exception {
        var f = new TerminalRateLimitFilter(new com.mara.platform.ratelimit.FixedWindowLimiter(3, java.time.Duration.ofMinutes(1), 100), clock);
        var reached = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            assertThat(hit(f, "/v1/terminal/sync/journal", "10.0.0.1", null, reached)).isEqualTo(200);
        }
        var req = new MockHttpServletRequest("POST", "/v1/terminal/sync/journal");
        req.setRemoteAddr("10.0.0.1");
        var res = new MockHttpServletResponse();
        f.doFilter(req, res, (a, b) -> reached.incrementAndGet());
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("60");
        assertThat(reached).hasValue(3);
    }

    @Test
    void aClientCannotPickItsOwnBucketWithXForwardedFor() throws Exception {
        var f = new TerminalRateLimitFilter(new com.mara.platform.ratelimit.FixedWindowLimiter(2, java.time.Duration.ofMinutes(1), 100), clock);
        var reached = new AtomicInteger();
        assertThat(hit(f, "/v1/terminal/fiscal/leases", "10.0.0.9", "1.1.1.1", reached)).isEqualTo(200);
        assertThat(hit(f, "/v1/terminal/fiscal/leases", "10.0.0.9", "2.2.2.2", reached)).isEqualTo(200);
        assertThat(hit(f, "/v1/terminal/fiscal/leases", "10.0.0.9", "3.3.3.3", reached)).isEqualTo(429);
    }

    @Test
    void otherAddressesAreUnaffected() throws Exception {
        var f = new TerminalRateLimitFilter(new com.mara.platform.ratelimit.FixedWindowLimiter(1, java.time.Duration.ofMinutes(1), 100), clock);
        var reached = new AtomicInteger();
        assertThat(hit(f, "/v1/terminal/sync/status", "10.0.0.1", null, reached)).isEqualTo(200);
        assertThat(hit(f, "/v1/terminal/sync/status", "10.0.0.1", null, reached)).isEqualTo(429);
        assertThat(hit(f, "/v1/terminal/sync/status", "10.0.0.2", null, reached)).isEqualTo(200);
    }

    @Test
    void onlyTerminalPathsAreCounted() throws Exception {
        var f = new TerminalRateLimitFilter(new com.mara.platform.ratelimit.FixedWindowLimiter(1, java.time.Duration.ofMinutes(1), 100), clock);
        var reached = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            assertThat(hit(f, "/actuator/health", "10.0.0.1", null, reached)).isEqualTo(200);
        }
        assertThat(reached).hasValue(5);
    }
}
