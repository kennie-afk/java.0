package com.mara.kit.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.mara.kit.tenant.TenantContext;
import com.mara.platform.credential.OperatorToken;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OperatorAuthFilterTest {

    private final String token = OperatorToken.mint().token();

    private record Seen(String scope, String tenantHeader) {
    }

    private MockHttpServletResponse run(OperatorAuthFilter filter, String method, String path, String bearer,
                                        String tenant, AtomicReference<String> tenantSeenDownstream) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        if (bearer != null) {
            request.addHeader("Authorization", "Bearer " + bearer);
        }
        if (tenant != null) {
            request.addHeader("X-Mara-Tenant", tenant);
        }
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                tenantSeenDownstream.set(((HttpServletRequest) req).getHeader("X-Mara-Tenant") + "|" + TenantContext.current());
                ((MockHttpServletResponse) res).setStatus(200);
            }
        };
        filter.doFilter(request, response, chain);
        return response;
    }

    private static CredentialVerifier verdict(CredentialVerifier.Decision d, AtomicReference<Seen> seen) {
        return r -> {
            seen.set(new Seen(r.requiredScope(), r.tenantHeader()));
            return d;
        };
    }

    @Test
    void eachEndpointAsksForExactlyItsOwnScope() throws Exception {
        var seen = new AtomicReference<Seen>();
        var f = new OperatorAuthFilter(verdict(CredentialVerifier.Decision.allowed("id", "l", null), seen));
        var down = new AtomicReference<String>();
        String[][] cases = {
                {"POST", "/v1/admin/tenants", "platform:tenants"},
                {"GET", "/v1/admin/credentials", "credentials:manage"},
                {"POST", "/v1/admin/credentials/revoke", "credentials:manage"},
                {"GET", "/v1/admin/staff", "admin:read"},
                {"POST", "/v1/admin/staff", "admin:write"},
                {"DELETE", "/v1/admin/staff", "admin:write"},
                {"GET", "/v1/internal/terminals/TERM-0123456789ABCDEF0123", "terminals:lookup"},
                {"POST", "/v1/internal/credentials/verify", "credentials:verify"},
                {"GET", "/v1/internal/chains", "sync:feed"},
                {"GET", "/v1/internal/terminals/TERM-0123456789ABCDEF0123/entries", "sync:feed"},
        };
        for (String[] c : cases) {
            assertThat(run(f, c[0], c[1], token, "T1", down).getStatus()).as(c[0] + " " + c[1]).isEqualTo(200);
            assertThat(seen.get().scope()).as(c[0] + " " + c[1]).isEqualTo(c[2]);
        }
    }

    @Test
    void anUnassignedPathAndAMalformedTokenNeverReachTheVerifier() throws Exception {
        var seen = new AtomicReference<Seen>();
        var f = new OperatorAuthFilter(verdict(CredentialVerifier.Decision.allowed("id", "l", null), seen));
        var down = new AtomicReference<String>();
        assertThat(run(f, "GET", "/v1/internal/unlisted", token, null, down).getStatus()).isEqualTo(403);
        assertThat(run(f, "GET", "/v1/internal/terminals/", token, null, down).getStatus()).isEqualTo(403);
        assertThat(run(f, "PUT", "/v1/internal/chains", token, null, down).getStatus()).isEqualTo(403);
        assertThat(run(f, "GET", "/v1/admin/staff", null, "T1", down).getStatus()).isEqualTo(401);
        assertThat(run(f, "GET", "/v1/admin/staff", "test-admin-token-0123456789-abcdef", "T1", down).getStatus()).isEqualTo(401);
        assertThat(seen.get()).isNull();
        assertThat(down.get()).isNull();
    }

    @Test
    void aTenantBoundCredentialOverwritesWhateverTenantTheCallerSent() throws Exception {
        var seen = new AtomicReference<Seen>();
        var f = new OperatorAuthFilter(verdict(CredentialVerifier.Decision.allowed("id", "l", "TEN-OWN"), seen));
        var down = new AtomicReference<String>();
        run(f, "GET", "/v1/admin/staff", token, null, down);
        assertThat(down.get()).startsWith("TEN-OWN|");
        run(f, "GET", "/v1/admin/staff", token, "TEN-OTHER", down);   // the verifier would refuse this; if it did not, the header is still forced
        assertThat(down.get()).startsWith("TEN-OWN|");
        assertThat(TenantContext.current()).as("cleared after the request").isNull();
    }

    @Test
    void aPlatformCredentialNamesItsOwnTenantAndRefusalsCarryTheirStatus() throws Exception {
        var seen = new AtomicReference<Seen>();
        var down = new AtomicReference<String>();
        var allow = new OperatorAuthFilter(verdict(CredentialVerifier.Decision.allowed("id", "l", null), seen));
        run(allow, "GET", "/v1/admin/staff", token, "TEN-NAMED", down);
        assertThat(down.get()).startsWith("TEN-NAMED|");
        for (int status : new int[] {401, 403, 503}) {
            var deny = new OperatorAuthFilter(verdict(CredentialVerifier.Decision.denied(status, "why"), seen));
            var res = run(deny, "GET", "/v1/admin/staff", token, "TEN-NAMED", down);
            assertThat(res.getStatus()).isEqualTo(status);
            // a 401 never says why; a 403 may, because the caller is already authenticated
            assertThat(res.getContentAsString()).contains(status == 401 ? "unauthorised" : "why");
        }
    }

    @Test
    void routesOutsideTheGuardedPrefixesAreLeftAlone() throws Exception {
        var seen = new AtomicReference<Seen>();
        var f = new OperatorAuthFilter(verdict(CredentialVerifier.Decision.denied(401, "x"), seen));
        var down = new AtomicReference<String>();
        assertThat(run(f, "GET", "/v1/terminal/sync/status", null, null, down).getStatus()).isEqualTo(200);
        assertThat(run(f, "GET", "/actuator/health", null, null, down).getStatus()).isEqualTo(200);
        assertThat(run(f, "GET", "/v1/administrators", null, null, down).getStatus()).as("prefix match is by path segment").isEqualTo(200);
        assertThat(seen.get()).isNull();
    }
}
