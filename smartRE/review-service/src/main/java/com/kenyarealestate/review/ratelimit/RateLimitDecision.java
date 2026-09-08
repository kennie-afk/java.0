package com.kenyarealestate.review.ratelimit;

/**
 * @param allowed          whether the request may proceed
 * @param tokensRemaining  what is left in the caller's bucket, for the response header
 * @param retryAfterSeconds how long until enough tokens exist; zero when allowed
 */
public record RateLimitDecision(boolean allowed, long tokensRemaining, long retryAfterSeconds) {

    /** Used when the limiter cannot reach Redis. See {@link TokenBucketLimiter}. */
    static RateLimitDecision unlimited() {
        return new RateLimitDecision(true, -1, 0);
    }
}
