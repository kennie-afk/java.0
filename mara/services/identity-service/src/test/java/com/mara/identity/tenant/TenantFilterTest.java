package com.mara.identity.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * No database, no Spring context — just the filter and the servlet mocks
 * {@code spring-test} already ships. Exists to pin the exact real path
 * {@code EnrolmentController} is mapped at, since a mismatch here previously made
 * enrolment unreachable in every real deployment while every existing test — which
 * builds {@link com.mara.identity.enrolment.EnrolmentService} by hand — passed anyway.
 */
@DisplayName("TenantFilter: which paths run without a tenant")
class TenantFilterTest {

    private final TenantFilter filter = new TenantFilter();

    @Test
    @DisplayName("the real enrolment path passes through with no tenant header")
    void enrolmentPathIsUnscoped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/enrolment");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.called).as("request must reach the controller").isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("a path that merely starts with the enrolment path is NOT exempt")
    void similarlyPrefixedPathStillNeedsATenant() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/enrolment-report");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.called).as("must not reach the controller unscoped").isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("a tenant-scoped request without the header is refused")
    void scopedRequestWithoutHeaderIsRefused() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/terminal");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.called).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("a tenant-scoped request with the header binds TenantContext for the call and clears it after")
    void scopedRequestBindsAndClearsTenant() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/terminal");
        request.addHeader(TenantFilter.TENANT_HEADER, "TEN-A");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenDuringCall = new String[1];
        FilterChain chain = (req, res) -> seenDuringCall[0] = TenantContext.current();

        filter.doFilter(request, response, chain);

        assertThat(seenDuringCall[0]).isEqualTo("TEN-A");
        assertThat(TenantContext.current()).as("must be cleared once the request completes").isNull();
    }

    private static final class RecordingChain implements FilterChain {
        boolean called;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            called = true;
        }
    }
}
