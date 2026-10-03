package com.mara.kit.auth;

import com.mara.kit.net.ClientAddress;
import com.mara.platform.ratelimit.RateLimiter;
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
 * <p>Keyed on the socket address by default, not {@code X-Forwarded-For}: that header is
 * client-supplied unless a trusted proxy controls its last entry, so trusting it blindly would let
 * a caller pick their own bucket. Behind the ingress, {@code mara.ratelimit.trust-forwarded-for}
 * switches to the ingress-appended entry (see {@link ClientAddress}).
 * Every till behind one shop's NAT shares a bucket, which is why the default is generous.
 */
public class TerminalRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final Clock clock;
    private final boolean trustForwardedFor;

    public TerminalRateLimitFilter(RateLimiter limiter, Clock clock) {
        this(limiter, clock, false);
    }

    /** {@code trustForwardedFor}: count the ingress-appended client address; see {@link ClientAddress}. */
    public TerminalRateLimitFilter(RateLimiter limiter, Clock clock, boolean trustForwardedFor) {
        this.limiter = limiter;
        this.clock = clock;
        this.trustForwardedFor = trustForwardedFor;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(TerminalAuthFilter.PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = ClientAddress.of(request, trustForwardedFor);
        RateLimiter.Decision decision = limiter.acquire(key, clock.instant());
        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limited\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
