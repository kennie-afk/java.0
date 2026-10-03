package com.hms.platform.security;

import com.hms.platform.config.HmsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Throttles the two endpoints reachable without a token: sign-in and organisation onboarding. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class PublicRateLimitFilter extends OncePerRequestFilter {

    private final FixedWindowLimiter login;
    private final FixedWindowLimiter onboarding;
    private final FixedWindowLimiter portal;
    private final boolean trustForwarded;

    PublicRateLimitFilter(HmsProperties props) {
        this.login = new FixedWindowLimiter(props.security().loginPerMinute(), 60_000L);
        this.portal = new FixedWindowLimiter(props.security().loginPerMinute(), 60_000L);
        this.onboarding = new FixedWindowLimiter(props.security().onboardingPerHour(), 3_600_000L);
        this.trustForwarded = props.security().trustForwardedFor();
    }

    private FixedWindowLimiter limiterFor(String path) {
        return switch (path) {
            case "/v1/auth/login" -> login;
            case "/v1/organisations" -> onboarding;
            case "/portal/auth/login", "/portal/auth/activate" -> portal;
            default -> null;
        };
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || limiterFor(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var decision = limiterFor(request.getRequestURI()).hit(address(request));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,\"code\":\"rate_limited\",\"detail\":\"Too many attempts. Try again in "
                + decision.retryAfterSeconds() + " seconds.\"}");
    }

    private String address(HttpServletRequest request) {
        if (trustForwarded) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
