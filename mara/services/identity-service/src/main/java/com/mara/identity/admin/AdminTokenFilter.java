package com.mara.identity.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards {@code /v1/admin/**} with one platform operator token.
 *
 * <p>Provisioning (creating a tenant, issuing an enrolment code) is what the vendor's
 * operator or an owner's console does, never a terminal. The token has no default: a
 * service without {@code MARA_ADMIN_TOKEN} of at least 24 characters refuses to start,
 * because a defaulted credential that silently works in production is worse than a
 * service that visibly does not.
 *
 * <p>Runs before {@code TenantFilter} so an unauthenticated caller learns nothing about
 * tenant scoping either.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminTokenFilter extends OncePerRequestFilter {

    public static final int MIN_TOKEN_LENGTH = 24;

    private final byte[] expected;

    public AdminTokenFilter(@Value("${mara.admin.token}") String token) {
        if (token == null || token.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException(
                    "MARA_ADMIN_TOKEN must be set to at least " + MIN_TOKEN_LENGTH + " characters");
        }
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/v1/admin") || path.startsWith("/v1/admin/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        byte[] presented = header != null && header.startsWith("Bearer ")
                ? header.substring(7).trim().getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        // Constant-time comparison: a byte-by-byte early exit leaks the token's prefix.
        if (!MessageDigest.isEqual(expected, presented)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"unauthorised\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
