package com.mara.kit.auth;

import com.mara.platform.ratelimit.FixedWindowLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps requests per source address on {@code /v1/terminal/**}, before the signature is even
 * looked at: a refused signature still costs a directory lookup and an Ed25519 verification, so
 * an unauthenticated flood must not get to spend them.
 *
 * <p>Keyed on the socket address, not {@code X-Forwarded-For}: that header is client-supplied
 * unless a trusted proxy overwrites it, so trusting it would let a caller pick their own bucket.
 * Every till behind one shop's NAT shares a bucket, which is why the default is generous.
 */
public class TerminalRateLimitFilter extends OncePerRequestFilter {

    private final FixedWindowLimiter limiter;
    private final Clock clock;

    public TerminalRateLimitFilter(int perMinute, Clock clock) {
        this.limiter = new FixedWindowLimiter(perMinute, Duration.ofMinutes(1), 20_000);
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(TerminalAuthFilter.PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr();
        if (!limiter.tryAcquire(key, clock.instant())) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(limiter.retryAfterSeconds(key, clock.instant())));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limited\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
