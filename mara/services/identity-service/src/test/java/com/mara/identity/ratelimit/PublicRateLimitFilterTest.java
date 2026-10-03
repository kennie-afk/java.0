package com.mara.identity.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PublicRateLimitFilterTest {

    private int hit(PublicRateLimitFilter f, String path, String addr, AtomicInteger reached) throws Exception {
        var req = new MockHttpServletRequest("POST", path);
        req.setRemoteAddr(addr);
        var res = new MockHttpServletResponse();
        f.doFilter(req, res, (a, b) -> reached.incrementAndGet());
        return res.getStatus();
    }

    @Test
    void enrolmentIsCappedPerAddress() throws Exception {
        var f = new PublicRateLimitFilter(2, 100, "", false);
        var reached = new AtomicInteger();
        assertThat(hit(f, "/v1/enrolment", "10.0.0.1", reached)).isEqualTo(200);
        assertThat(hit(f, "/v1/enrolment", "10.0.0.1", reached)).isEqualTo(200);
        assertThat(hit(f, "/v1/enrolment", "10.0.0.1", reached)).isEqualTo(429);
        assertThat(hit(f, "/v1/enrolment", "10.0.0.2", reached)).isEqualTo(200);
        assertThat(reached).hasValue(3);
    }

    @Test
    void staffSignInIsCappedAcrossTerminalsFromOneAddress() throws Exception {
        var f = new PublicRateLimitFilter(100, 2, "", false);
        var reached = new AtomicInteger();
        assertThat(hit(f, "/v1/terminals/TERM-AAAAAAAAAAAAAAAAAAAA/staff-signin", "10.0.0.1", reached)).isEqualTo(200);
        assertThat(hit(f, "/v1/terminals/TERM-BBBBBBBBBBBBBBBBBBBB/staff-signin", "10.0.0.1", reached)).isEqualTo(200);
        assertThat(hit(f, "/v1/terminals/TERM-CCCCCCCCCCCCCCCCCCCC/staff-signin", "10.0.0.1", reached)).isEqualTo(429);
    }

    @Test
    void enrolmentAndSignInHaveSeparateBudgets() throws Exception {
        var f = new PublicRateLimitFilter(1, 1, "", false);
        var reached = new AtomicInteger();
        assertThat(hit(f, "/v1/enrolment", "10.0.0.1", reached)).isEqualTo(200);
        assertThat(hit(f, "/v1/terminals/TERM-AAAAAAAAAAAAAAAAAAAA/staff-signin", "10.0.0.1", reached)).isEqualTo(200);
    }

    @Test
    void otherPathsAreNeverCounted() throws Exception {
        var f = new PublicRateLimitFilter(1, 1, "", false);
        var reached = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            assertThat(hit(f, "/actuator/health", "10.0.0.1", reached)).isEqualTo(200);
            assertThat(hit(f, "/v1/admin/staff", "10.0.0.1", reached)).isEqualTo(200);
        }
        assertThat(reached).hasValue(10);
    }
}
