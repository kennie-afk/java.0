package com.soko.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Throttles the three unauthenticated endpoints an attacker can reach without a token: sign-in
 * (password guessing), registration (account spam) and password reset (email/SMS spam). Limits are
 * per client address and configurable under {@code soko.ratelimit}.
 *
 * <p>The client address is the socket peer unless {@code soko.ratelimit.trust-forwarded} is true,
 * in which case the first {@code X-Forwarded-For} entry is used. Turn that on only behind a proxy
 * that overwrites the header; otherwise a client can pick its own address and dodge the limit.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final FixedWindowLimiter login;
    private final FixedWindowLimiter register;
    private final FixedWindowLimiter forgot;
    private final boolean trustForwarded;

    public AuthRateLimitFilter(
            @Value("${soko.ratelimit.login-per-minute:10}") int loginPerMinute,
            @Value("${soko.ratelimit.register-per-hour:10}") int registerPerHour,
            @Value("${soko.ratelimit.forgot-per-hour:5}") int forgotPerHour,
            @Value("${soko.ratelimit.trust-forwarded:false}") boolean trustForwarded) {
        this.login = new FixedWindowLimiter(loginPerMinute, 60_000L);
        this.register = new FixedWindowLimiter(registerPerHour, 3_600_000L);
        this.forgot = new FixedWindowLimiter(forgotPerHour, 3_600_000L);
        this.trustForwarded = trustForwarded;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || limiterFor(request.getRequestURI()) == null;
    }

    private FixedWindowLimiter limiterFor(String path) {
        return switch (path) {
            case "/v1/auth/login" -> login;
            case "/v1/auth/register" -> register;
            case "/v1/auth/forgot" -> forgot;
            default -> null;
        };
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        FixedWindowLimiter.Decision decision =
                limiterFor(request.getRequestURI()).hit(clientAddress(request));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setContentType("application/problem+json");
        response.getWriter()
                .write(
                        "{\"title\":\"too many requests\",\"status\":429,\"detail\":"
                                + "\"Too many attempts. Try again in "
                                + decision.retryAfterSeconds()
                                + " seconds.\",\"code\":\"rate_limited\"}");
    }

    private String clientAddress(HttpServletRequest request) {
        if (trustForwarded) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
