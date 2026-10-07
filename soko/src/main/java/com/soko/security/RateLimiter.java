package com.soko.security;

/** Counts hits per key in a window; implementations differ in whether the count is shared between replicas. */
public interface RateLimiter {

    /** The answer to one {@link #hit}: whether it is allowed, and how long until the window resets. */
    record Decision(boolean allowed, long retryAfterSeconds) {}

    Decision hit(String key);
}
