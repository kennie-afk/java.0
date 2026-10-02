package com.mara.kit.auth;

import com.mara.kit.tenant.TenantContext;
import com.mara.platform.identity.RequestSignature;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates {@code /v1/terminal/**} requests as an enrolled terminal and binds the tenant.
 *
 * <p>The tenant is taken from identity-service's record of the terminal, never from anything
 * in the request, so a terminal cannot name another shop's tenant. Every failure (unknown
 * terminal, inactive terminal, stale or forged request, oversized body) answers the same 401
 * so the response teaches a probe nothing about which check failed.
 */
public class TerminalAuthFilter extends OncePerRequestFilter {

    public static final String PREFIX = "/v1/terminal/";
    public static final String TERMINAL_ATTRIBUTE = "mara.terminal";
    public static final int MAX_BODY_BYTES = 4 * 1024 * 1024;

    private final TerminalDirectory directory;
    private final Clock clock;

    public TerminalAuthFilter(TerminalDirectory directory, Clock clock) {
        this.directory = directory;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String terminalId = request.getHeader("X-Mara-Terminal");
        String stamp = request.getHeader("X-Mara-Timestamp");
        String signature = request.getHeader("X-Mara-Signature");
        if (terminalId == null || stamp == null || signature == null || request.getContentLengthLong() > MAX_BODY_BYTES) {
            refuse(response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        long epochSecond;
        try {
            epochSecond = Long.parseLong(stamp);
        } catch (NumberFormatException e) {
            refuse(response);
            return;
        }
        if (body.length > MAX_BODY_BYTES || !RequestSignature.isFresh(epochSecond, clock.instant())) {
            refuse(response);
            return;
        }
        // The signed path includes the query string: it selects what is read.
        String target = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        TerminalRecord terminal = directory.find(terminalId).orElse(null);
        boolean ok = terminal != null && terminal.active()
                && RequestSignature.verify(
                        terminal.publicKey(), terminalId, epochSecond, request.getMethod(), target, body, signature);
        if (!ok) {
            refuse(response);
            return;
        }
        request.setAttribute(TERMINAL_ATTRIBUTE, terminal);
        TenantContext.set(terminal.tenantId());
        try {
            chain.doFilter(new CachedBodyRequest(request, body), response);
        } finally {
            TenantContext.clear();
        }
    }

    private static void refuse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"unauthorised\"}");
    }
}
