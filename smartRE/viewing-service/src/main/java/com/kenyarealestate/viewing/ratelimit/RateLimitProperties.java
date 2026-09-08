package com.kenyarealestate.viewing.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Per-service throttling limits.
 *
 * <p>These sit behind the gateway's own limiter and are deliberately looser: the gateway
 * is the primary throttle for traffic that comes through the front door, and this exists
 * for traffic that does not — a caller inside the cluster holding a valid JWT, a
 * misbehaving sibling service, or a gateway that has been bypassed. A limit tight enough
 * to be the only defence would reject legitimate gateway-shaped traffic.
 */
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /** Whether to throttle at all. Off only makes sense in tests. */
    private boolean enabled = true;

    /** Maximum burst a single caller may spend at once. */
    private int capacity = 300;

    /** Sustained rate, requests per minute, that the bucket refills at. */
    private int refillPerMinute = 300;

    /** Stricter bucket for anything that changes state. */
    private int writeCapacity = 60;

    private int writeRefillPerMinute = 60;

    /**
     * Whether X-Forwarded-For may be used to identify an unauthenticated caller. Only
     * safe where the gateway is the sole route to this service; see RateLimitFilter.
     */
    private boolean trustForwardedFor = true;

    public boolean isTrustForwardedFor() { return trustForwardedFor; }
    public void setTrustForwardedFor(boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public int getRefillPerMinute() { return refillPerMinute; }
    public void setRefillPerMinute(int refillPerMinute) { this.refillPerMinute = refillPerMinute; }

    public int getWriteCapacity() { return writeCapacity; }
    public void setWriteCapacity(int writeCapacity) { this.writeCapacity = writeCapacity; }

    public int getWriteRefillPerMinute() { return writeRefillPerMinute; }
    public void setWriteRefillPerMinute(int writeRefillPerMinute) {
        this.writeRefillPerMinute = writeRefillPerMinute;
    }
}
