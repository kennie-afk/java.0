package com.mara.identity.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds the tenant for the life of one request, from the header the gateway sets.
 *
 * <p>The gateway verifies the caller's token and asserts the tenant; this service does
 * not re-derive it. That division only holds if the header cannot be spoofed by
 * anything that reaches this service directly, which is why the deployment puts these
 * services on a network the gateway alone can address, and why the header is stripped
 * from inbound traffic at the edge.
 *
 * <p>Ordered before everything else, because a filter that ran first and touched the
 * database would do so with no tenant bound.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantFilter extends OncePerRequestFilter {

    /** Set by the gateway from a verified token claim. Never trusted from a client. */
    public static final String TENANT_HEADER = "X-Mara-Tenant";

    /**
     * Paths that legitimately run without a tenant.
     *
     * <p>Enrolment is the notable one: a device presenting a code is not yet anybody,
     * so it has no tenant to assert. That is why {@code resolve_enrolment} is a
     * SECURITY DEFINER function rather than an ordinary query — it is the single
     * operation allowed to cross the boundary, and it takes a hash rather than a
     * plaintext code so it cannot be used to enumerate.
     */
    private static final List<String> UNSCOPED_PATHS = List.of(
            "/v1/enrolment/",
            "/actuator/health",
            "/actuator/info");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String tenantId = trimmedHeader(request);
        boolean unscoped = isUnscoped(request.getRequestURI());

        if (tenantId == null && !unscoped) {
            // Fail closed. Without a tenant the policies would return nothing anyway,
            // but a 500 from an empty result set is a worse answer than saying plainly
            // that the request was not scoped.
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "no tenant context");
            return;
        }

        if (tenantId != null) {
            TenantContext.set(tenantId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // Always, on every path including exceptions. A pooled thread that keeps a
            // tenant is how one shop reads another's data.
            TenantContext.clear();
        }
    }

    private static String trimmedHeader(HttpServletRequest request) {
        String value = request.getHeader(TENANT_HEADER);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isUnscoped(String path) {
        return UNSCOPED_PATHS.stream().anyMatch(path::startsWith);
    }
}
