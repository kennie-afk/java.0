package com.kenyarealestate.verification.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Throttles callers at this service, independently of the gateway.
 *
 * <p>Runs after authentication so it can key on the caller's identity: a shared office
 * NAT would otherwise put every user behind one IP into the same bucket. Unauthenticated
 * requests fall back to the client address, which is the best identity available.
 *
 * <p>Actuator and internal service-to-service paths are exempt. Health probes must never
 * be throttled — a limiter that can make a pod look unhealthy will eventually take the
 * whole deployment down — and internal callers are already gated by a shared secret and
 * are not the abuse case this exists for.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final TokenBucketLimiter limiter;
    private final RateLimitProperties props;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(TokenBucketLimiter limiter, RateLimitProperties props, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.isEnabled()) return true;
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.contains("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        boolean write = isStateChanging(request.getMethod());
        String key = "rl:" + (write ? "w:" : "r:") + callerIdentity(request);

        RateLimitDecision decision = write
                ? limiter.tryConsume(key, props.getWriteCapacity(), props.getWriteRefillPerMinute())
                : limiter.tryConsume(key, props.getCapacity(), props.getRefillPerMinute());

        if (decision.tokensRemaining() >= 0) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.tokensRemaining()));
        }

        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit exceeded for {} on {} {}", key, request.getMethod(), request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "timestamp", Instant.now().toString(),
                "status", 429,
                "error", "Too Many Requests",
                "message", "Rate limit exceeded. Retry in " + decision.retryAfterSeconds() + "s.",
                "path", request.getRequestURI()));
    }

    private static boolean isStateChanging(String method) {
        return !("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method));
    }

    /**
     * The authenticated user where there is one, else the client address. The user id is
     * set as a request attribute by the JWT filter, which has already run.
     *
     * <p>X-Forwarded-For is only consulted when {@code rate-limit.trust-forwarded-for} is
     * set, and that flag is a statement about the network, not a preference. The header
     * is attacker-controlled: anyone who can reach a pod directly can send a fresh value
     * per request and give themselves an unlimited number of buckets. It is safe only
     * where nothing outside the cluster can reach this pod, which is what
     * {@code k8s/network-policy.yaml} enforces — external traffic terminates at the
     * gateway, and only in-namespace pods may open a connection here. Where that is not
     * true, set it false and accept that unauthenticated callers share one coarse bucket
     * per source address, which is less precise but cannot be forged.
     */
    private String callerIdentity(HttpServletRequest request) {
        Object userId = request.getAttribute("authenticatedUserId");
        if (userId != null) return "user:" + userId;

        if (props.isTrustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                // Left-most entry is the original client; the rest are proxies.
                return "ip:" + forwarded.split(",")[0].trim();
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}
