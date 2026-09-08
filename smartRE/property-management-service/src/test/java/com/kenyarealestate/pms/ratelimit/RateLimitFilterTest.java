package com.kenyarealestate.pms.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitFilterTest {

    @Mock private TokenBucketLimiter limiter;
    @Mock private FilterChain chain;

    private RateLimitProperties props;
    private RateLimitFilter filter;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        props = new RateLimitProperties();
        filter = new RateLimitFilter(limiter, props, new ObjectMapper());
        response = new MockHttpServletResponse();
        allow();
    }

    private void allow() {
        when(limiter.tryConsume(any(), anyInt(), anyInt()))
                .thenReturn(new RateLimitDecision(true, 42, 0));
    }

    private void deny() {
        when(limiter.tryConsume(any(), anyInt(), anyInt()))
                .thenReturn(new RateLimitDecision(false, 0, 17));
    }

    private String keyFor(MockHttpServletRequest req) throws Exception {
        filter.doFilter(req, response, chain);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(limiter).tryConsume(key.capture(), anyInt(), anyInt());
        return key.getValue();
    }

    @Nested
    @DisplayName("what gets throttled")
    class Scope {

        @Test
        @DisplayName("health probes are never throttled — a limiter that can fail a probe can take the deployment down")
        void actuatorExempt() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
            req.setRequestURI("/actuator/health");
            filter.doFilter(req, response, chain);
            verify(chain).doFilter(req, response);
            verifyNoInteractions(limiter);
        }

        @Test
        @DisplayName("internal service-to-service paths are exempt — they are gated by a shared secret, not by volume")
        void internalExempt() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/pms/leases/internal/sync");
            req.setRequestURI("/api/pms/leases/internal/sync");
            filter.doFilter(req, response, chain);
            verifyNoInteractions(limiter);
        }

        @Test
        @DisplayName("disabling the limiter lets everything through")
        void disabled() throws Exception {
            props.setEnabled(false);
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/pms/leases");
            req.setRequestURI("/api/pms/leases");
            filter.doFilter(req, response, chain);
            verifyNoInteractions(limiter);
        }
    }

    @Nested
    @DisplayName("who the bucket belongs to")
    class Identity {

        @Test
        @DisplayName("an authenticated caller is keyed by user, so an office behind one NAT is not one bucket")
        void keyedByUser() throws Exception {
            UUID userId = UUID.randomUUID();
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/pms/leases");
            req.setRequestURI("/api/pms/leases");
            req.setAttribute("authenticatedUserId", userId);
            req.setRemoteAddr("10.0.0.5");
            assertThat(keyFor(req)).isEqualTo("rl:r:user:" + userId);
        }

        @Test
        @DisplayName("an anonymous caller falls back to the forwarded client address")
        void keyedByForwardedAddress() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/pms/leases");
            req.setRequestURI("/api/pms/leases");
            req.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
            req.setRemoteAddr("10.0.0.1");
            assertThat(keyFor(req)).isEqualTo("rl:r:ip:203.0.113.9");
        }

        @Test
        @DisplayName("with the forwarded header untrusted, a spoofed value cannot buy a fresh bucket")
        void forwardedHeaderIgnoredWhenUntrusted() throws Exception {
            props.setTrustForwardedFor(false);
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/pms/leases");
            req.setRequestURI("/api/pms/leases");
            req.addHeader("X-Forwarded-For", "1.2.3.4");
            req.setRemoteAddr("10.0.0.1");
            assertThat(keyFor(req)).isEqualTo("rl:r:ip:10.0.0.1");
        }

        @Test
        @DisplayName("an authenticated caller is keyed by user even when they send a forwarded header")
        void userWinsOverForwardedHeader() throws Exception {
            UUID userId = UUID.randomUUID();
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/pms/leases");
            req.setRequestURI("/api/pms/leases");
            req.setAttribute("authenticatedUserId", userId);
            req.addHeader("X-Forwarded-For", "1.2.3.4");
            assertThat(keyFor(req)).isEqualTo("rl:r:user:" + userId);
        }
    }

    @Nested
    @DisplayName("reads and writes are separate buckets")
    class Buckets {

        @Test
        @DisplayName("a write is charged to the stricter write bucket")
        void writeUsesWriteLimits() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/pms/leases");
            req.setRequestURI("/api/pms/leases");
            req.setRemoteAddr("10.0.0.1");
            filter.doFilter(req, response, chain);
            verify(limiter).tryConsume(eq("rl:w:ip:10.0.0.1"),
                    eq(props.getWriteCapacity()), eq(props.getWriteRefillPerMinute()));
        }

        @Test
        @DisplayName("a read is charged to the looser read bucket")
        void readUsesReadLimits() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/pms/leases");
            req.setRequestURI("/api/pms/leases");
            req.setRemoteAddr("10.0.0.1");
            filter.doFilter(req, response, chain);
            verify(limiter).tryConsume(eq("rl:r:ip:10.0.0.1"),
                    eq(props.getCapacity()), eq(props.getRefillPerMinute()));
        }

        @Test
        @DisplayName("exhausting reads does not block writes — they are different keys")
        void readAndWriteKeysDiffer() throws Exception {
            MockHttpServletRequest read = new MockHttpServletRequest("GET", "/api/pms/leases");
            read.setRequestURI("/api/pms/leases");
            read.setRemoteAddr("10.0.0.1");
            MockHttpServletRequest write = new MockHttpServletRequest("DELETE", "/api/pms/leases/1");
            write.setRequestURI("/api/pms/leases/1");
            write.setRemoteAddr("10.0.0.1");

            filter.doFilter(read, response, chain);
            filter.doFilter(write, new MockHttpServletResponse(), chain);

            ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
            verify(limiter, times(2)).tryConsume(keys.capture(), anyInt(), anyInt());
            assertThat(keys.getAllValues()).containsExactly("rl:r:ip:10.0.0.1", "rl:w:ip:10.0.0.1");
        }
    }

    @Nested
    @DisplayName("what the caller is told")
    class Response {

        @Test
        @DisplayName("an allowed request passes through and reports what is left")
        void allowedRequestPasses() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/pms/leases");
            req.setRequestURI("/api/pms/leases");
            filter.doFilter(req, response, chain);
            verify(chain).doFilter(req, response);
            assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("42");
        }

        @Test
        @DisplayName("a throttled request is refused with 429, a Retry-After, and never reaches the controller")
        void throttledRequestRefused() throws Exception {
            deny();
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/pms/leases");
            req.setRequestURI("/api/pms/leases");
            filter.doFilter(req, response, chain);

            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(response.getHeader("Retry-After")).isEqualTo("17");
            assertThat(response.getContentAsString()).contains("Rate limit exceeded");
            verifyNoInteractions(chain);
        }

        @Test
        @DisplayName("when Redis is unreachable the request is allowed and no misleading header is sent")
        void failsOpen() throws Exception {
            when(limiter.tryConsume(any(), anyInt(), anyInt())).thenReturn(RateLimitDecision.unlimited());
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/pms/leases");
            req.setRequestURI("/api/pms/leases");
            filter.doFilter(req, response, chain);

            verify(chain).doFilter(req, response);
            assertThat(response.getHeader("X-RateLimit-Remaining")).isNull();
        }
    }
}
