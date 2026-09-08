package com.smartseason.agronomy.ratelimit;

public record RateLimitDecision(boolean allowed, long remaining, long retryAfterMillis) {

    public static RateLimitDecision unlimited() {
        return new RateLimitDecision(true, -1, 0);
    }
}
