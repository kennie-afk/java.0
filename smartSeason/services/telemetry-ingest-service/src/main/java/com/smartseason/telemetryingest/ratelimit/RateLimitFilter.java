package com.smartseason.telemetryingest.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String SERVICE = "telemetry-ingest-service";

    private final TokenBucketLimiter limiter;
    private final RateLimitProperties properties;

    public RateLimitFilter(TokenBucketLimiter limiter, RateLimitProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();

        return path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean write = isWrite(request.getMethod());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());

        String key;
        int capacity;
        double refill;

        if (authenticated) {
            key = "rl:" + SERVICE + ":" + (write ? "w" : "r") + ":" + tenant() + ":" + authentication.getName();
            capacity = write ? properties.getWriteCapacity() : properties.getReadCapacity();
            refill = write ? properties.getWriteRefillPerSecond() : properties.getReadRefillPerSecond();
        } else {
            key = "rl:" + SERVICE + ":anon:" + clientAddress(request);
            capacity = properties.getAnonymousCapacity();
            refill = properties.getAnonymousRefillPerSecond();
        }

        RateLimitDecision decision = limiter.take(key, capacity, refill);

        if (decision.remaining() >= 0) {
            response.setHeader("X-RateLimit-Limit", Integer.toString(capacity));
            response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        }

        if (!decision.allowed()) {
            long seconds = Math.max(1, decision.retryAfterMillis() / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(seconds));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"https://docs.smartseason.io/errors/rate-limited",\
"title":"rate-limited","status":429,\
"detail":"Too many requests. Try again in %d second(s).",\
"code":"rate-limited"}""".formatted(seconds));
            return;
        }

        chain.doFilter(request, response);
    }

    private static boolean isWrite(String method) {
        return HttpMethod.POST.matches(method)
                || HttpMethod.PUT.matches(method)
                || HttpMethod.PATCH.matches(method)
                || HttpMethod.DELETE.matches(method);
    }

    private static String tenant() {
        return com.smartseason.telemetryingest.platform.TenantContext.tenantId()
                .map(java.util.UUID::toString)
                .orElse("none");
    }

    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
