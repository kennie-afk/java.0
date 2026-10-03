package com.mara.identity.ratelimit;

import com.mara.kit.net.ClientAddress;
import com.mara.kit.ratelimit.RateLimiters;
import com.mara.platform.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps the two endpoints anyone on the network can reach without a credential: enrolment
 * (a code is the only proof) and staff sign-in (a PIN is). Both do database work per call, and
 * the PIN lockout only protects one staff member at a time, so without a cap one address could
 * still sweep every staff number in a shop.
 *
 * <p>Keyed on the socket address by default, not {@code X-Forwarded-For}, which a client can set.
 * Behind the ingress {@code mara.ratelimit.trust-forwarded-for} uses the entry the ingress appended
 * (see {@link ClientAddress}); only enable it when nothing else can reach this service.
 * Runs before the tenant filter so a refused flood never reaches the database.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final Pattern STAFF_SIGNIN = Pattern.compile("^/v1/terminals/[^/]+/staff-signin$");

    private final RateLimiter enrolment;
    private final RateLimiter signIn;
    private final Clock clock = Clock.systemUTC();
    private final boolean trustForwardedFor;

    public PublicRateLimitFilter(
            @Value("${mara.ratelimit.enrolment-per-minute:20}") int enrolmentPerMinute,
            @Value("${mara.ratelimit.signin-per-minute:120}") int signInPerMinute,
            @Value("${mara.redis.url:}") String redisUrl,
            @Value("${mara.ratelimit.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
        this.enrolment = RateLimiters.create("enrolment", redisUrl, enrolmentPerMinute, Duration.ofMinutes(1));
        this.signIn = RateLimiters.create("signin", redisUrl, signInPerMinute, Duration.ofMinutes(1));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/v1/enrolment") || STAFF_SIGNIN.matcher(path).matches());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimiter limiter = request.getRequestURI().equals("/v1/enrolment") ? enrolment : signIn;
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
