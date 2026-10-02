package com.mara.identity.internal;

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
 * Guards {@code /v1/internal/**}: the calls other Mara services make to the trust root.
 * One bearer token, no default, at least 24 characters, compared in constant time. It is a
 * different credential from the operator's admin token on purpose: a service that can look a
 * terminal's key up must not thereby be able to provision tenants.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalTokenFilter extends OncePerRequestFilter {

    private final byte[] expected;

    public InternalTokenFilter(@Value("${mara.internal.token}") String token) {
        if (token == null || token.length() < 24) {
            throw new IllegalStateException("MARA_INTERNAL_TOKEN must be set to at least 24 characters");
        }
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/v1/internal") || path.startsWith("/v1/internal/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        byte[] presented = header != null && header.startsWith("Bearer ")
                ? header.substring(7).trim().getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        if (!MessageDigest.isEqual(expected, presented)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"unauthorised\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
