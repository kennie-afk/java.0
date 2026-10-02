package com.mara.kit.auth;

import com.mara.kit.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards {@code /v1/admin/**} (the operator's console) and {@code /v1/internal/**}
 * (service to service) with one bearer token each, and binds the tenant a back-office caller
 * names in {@code X-Mara-Tenant}.
 *
 * <p>Neither token has a default and neither may be short: a service without them refuses to
 * start. Until a gateway issues staff tokens, this is the honest back-office boundary: an
 * operator credential, not a pretence of per-user login.
 */
public class OperatorTokenFilter extends OncePerRequestFilter {

    public static final int MIN_TOKEN_LENGTH = 24;

    private final byte[] adminToken;
    private final byte[] internalToken;

    public OperatorTokenFilter(String adminToken, String internalToken) {
        this.adminToken = require("MARA_ADMIN_TOKEN", adminToken);
        this.internalToken = require("MARA_INTERNAL_TOKEN", internalToken);
    }

    private static byte[] require(String name, String value) {
        if (value == null || value.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException(name + " must be set to at least " + MIN_TOKEN_LENGTH + " characters");
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean under(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(under(path, "/v1/admin") || under(path, "/v1/internal"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        byte[] expected = under(path, "/v1/admin") ? adminToken : internalToken;
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
        String tenant = request.getHeader("X-Mara-Tenant");
        if (tenant != null && !tenant.isBlank()) {
            TenantContext.set(tenant.trim());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
