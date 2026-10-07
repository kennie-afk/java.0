package com.soko.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Throttles the three unauthenticated endpoints an attacker can reach without a token: sign-in
 * (password guessing), registration (account spam) and password reset (email/SMS spam). Limits are
 * per client address and configurable under {@code soko.ratelimit}. Counts live in Postgres
 * ({@link SharedWindowLimiter}) so they hold across replicas; {@code soko.ratelimit.shared=false}
 * keeps them in process.
 *
 * <p>The client address is the socket peer unless {@code soko.ratelimit.trust-forwarded} is true,
 * in which case the first {@code X-Forwarded-For} entry is used. Turn that on only behind a proxy
 * that overwrites the header; otherwise a client can pick its own address and dodge the limit.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter login;
    private final RateLimiter register;
    private final RateLimiter forgot;
    private final RateLimiter reset;
    private final boolean trustForwarded;

    public AuthRateLimitFilter(
            @Value("${soko.ratelimit.login-per-minute:10}") int loginPerMinute,
            @Value("${soko.ratelimit.register-per-hour:10}") int registerPerHour,
            @Value("${soko.ratelimit.forgot-per-hour:5}") int forgotPerHour,
            @Value("${soko.ratelimit.reset-per-hour:20}") int resetPerHour,
            @Value("${soko.ratelimit.trust-forwarded:false}") boolean trustForwarded,
            @Value("${soko.ratelimit.shared:true}") boolean shared,
            ObjectProvider<JdbcTemplate> jdbc) {
        JdbcTemplate template = shared ? jdbc.getIfAvailable() : null;
        this.login = make(template, "login", loginPerMinute, 60_000L);
        this.register = make(template, "register", registerPerHour, 3_600_000L);
        this.forgot = make(template, "forgot", forgotPerHour, 3_600_000L);
        this.reset = make(template, "reset", resetPerHour, 3_600_000L);
        this.trustForwarded = trustForwarded;
    }

    /** Postgres-backed so the limit holds across replicas; per-process when no database template exists. */
    private static RateLimiter make(JdbcTemplate jdbc, String bucket, int limit, long windowMillis) {
        return jdbc == null
                ? new FixedWindowLimiter(limit, windowMillis)
                : new SharedWindowLimiter(jdbc, bucket, limit, windowMillis, System::currentTimeMillis);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || limiterFor(request.getRequestURI()) == null;
    }

    private RateLimiter limiterFor(String path) {
        return switch (path) {
            case "/v1/auth/login" -> login;
            case "/v1/auth/register" -> register;
            case "/v1/auth/forgot" -> forgot;
            case "/v1/auth/reset" -> reset;
            default -> null;
        };
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimiter.Decision decision =
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
