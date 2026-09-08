"""Per-service rate limiting, shared across replicas through Redis.

Every service gets the same filter. It runs after authentication so a caller is
counted as a person rather than an address: at scale, addresses are useless as
identity - a whole mobile network sits behind a handful of them, and one abusive
client can exhaust a limit shared by thousands of blameless ones.

The counter lives in Redis and is moved by a Lua script, so the check and the
decrement happen in one round trip and cannot interleave between replicas. When
Redis is unreachable the filter lets traffic through: rate limiting protects the
service from load, and refusing every request because the limiter is down is the
outage it was meant to prevent.
"""

TOKEN_BUCKET_LUA = '''-- Atomic token bucket.
--
-- KEYS[1]   bucket key
-- ARGV[1]   capacity           (tokens)
-- ARGV[2]   refill per second  (tokens)
-- ARGV[3]   now                (milliseconds)
-- ARGV[4]   cost               (tokens)
--
-- Returns { allowed, remaining, retry_after_millis }.
--
-- Check and consume have to be one operation. Read-then-write from several
-- replicas lets two callers both see the last token and both take it, which is
-- exactly the burst the limit exists to stop.

local capacity   = tonumber(ARGV[1])
local refill     = tonumber(ARGV[2])
local now        = tonumber(ARGV[3])
local cost       = tonumber(ARGV[4])

local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'updated')
local tokens = tonumber(bucket[1])
local updated = tonumber(bucket[2])

if tokens == nil then
  tokens = capacity
  updated = now
end

-- Refill for the time that has passed, never above capacity.
local elapsed = math.max(0, now - updated) / 1000.0
tokens = math.min(capacity, tokens + elapsed * refill)

local allowed = 0
local retry_after = 0

if tokens >= cost then
  tokens = tokens - cost
  allowed = 1
else
  -- How long until one more token is worth waiting for.
  retry_after = math.ceil(((cost - tokens) / refill) * 1000)
end

redis.call('HSET', KEYS[1], 'tokens', tokens, 'updated', now)

-- Expire an idle bucket rather than keeping a key per caller for ever. Two
-- refill windows is long enough that an active caller never loses its state.
local ttl = math.ceil((capacity / refill) * 2)
redis.call('EXPIRE', KEYS[1], math.max(ttl, 60))

return { allowed, math.floor(tokens), retry_after }
'''


PROPERTIES = '''package com.smartseason.{pkg}.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits, per caller per service.
 *
 * Reads and writes are counted separately because they cost differently and are
 * abused differently: a scraper hammers reads, while writes are what corrupt
 * data and cost money downstream.
 */
@ConfigurationProperties(prefix = "smartseason.ratelimit")
public class RateLimitProperties {{

    private boolean enabled = true;

    /** Requests a caller may make in a burst, and how fast the allowance returns. */
    private int readCapacity = 300;
    private double readRefillPerSecond = 5.0;

    private int writeCapacity = 60;
    private double writeRefillPerSecond = 1.0;

    /**
     * Unauthenticated callers are counted by address, which is coarse, so the
     * allowance is smaller: it exists to blunt credential stuffing and scraping
     * of public endpoints, not to serve real traffic.
     */
    private int anonymousCapacity = 30;
    private double anonymousRefillPerSecond = 0.5;

    public boolean isEnabled() {{ return enabled; }}
    public void setEnabled(boolean enabled) {{ this.enabled = enabled; }}

    public int getReadCapacity() {{ return readCapacity; }}
    public void setReadCapacity(int readCapacity) {{ this.readCapacity = readCapacity; }}

    public double getReadRefillPerSecond() {{ return readRefillPerSecond; }}
    public void setReadRefillPerSecond(double v) {{ this.readRefillPerSecond = v; }}

    public int getWriteCapacity() {{ return writeCapacity; }}
    public void setWriteCapacity(int writeCapacity) {{ this.writeCapacity = writeCapacity; }}

    public double getWriteRefillPerSecond() {{ return writeRefillPerSecond; }}
    public void setWriteRefillPerSecond(double v) {{ this.writeRefillPerSecond = v; }}

    public int getAnonymousCapacity() {{ return anonymousCapacity; }}
    public void setAnonymousCapacity(int anonymousCapacity) {{ this.anonymousCapacity = anonymousCapacity; }}

    public double getAnonymousRefillPerSecond() {{ return anonymousRefillPerSecond; }}
    public void setAnonymousRefillPerSecond(double v) {{ this.anonymousRefillPerSecond = v; }}
}}
'''


DECISION = '''package com.smartseason.{pkg}.ratelimit;

/** The outcome of one limit check. */
public record RateLimitDecision(boolean allowed, long remaining, long retryAfterMillis) {{

    /** Used when the limiter cannot answer, so traffic is let through. */
    public static RateLimitDecision unlimited() {{
        return new RateLimitDecision(true, -1, 0);
    }}
}}
'''


LIMITER = '''package com.smartseason.{pkg}.ratelimit;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * A token bucket held in Redis, so every replica of this service shares one
 * allowance per caller. Running the count in memory would multiply the limit by
 * the number of pods, which is the same as not having one.
 */
@Component
public class TokenBucketLimiter {{

    private static final Logger log = LoggerFactory.getLogger(TokenBucketLimiter.class);

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> script;

    public TokenBucketLimiter(StringRedisTemplate redis) {{
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new org.springframework.core.io.ClassPathResource("scripts/token-bucket.lua"));
        this.script.setResultType(List.class);
    }}

    public RateLimitDecision take(String key, int capacity, double refillPerSecond) {{
        try {{
            List<?> result = redis.execute(
                    script,
                    List.of(key),
                    Integer.toString(capacity),
                    Double.toString(refillPerSecond),
                    Long.toString(Instant.now().toEpochMilli()),
                    "1");

            if (result == null || result.size() < 3) {{
                return RateLimitDecision.unlimited();
            }}
            long allowed = ((Number) result.get(0)).longValue();
            long remaining = ((Number) result.get(1)).longValue();
            long retryAfter = ((Number) result.get(2)).longValue();
            return new RateLimitDecision(allowed == 1, remaining, retryAfter);
        }} catch (RuntimeException ex) {{
            // Redis is unreachable or the script failed. Letting traffic through
            // is the lesser harm: the limiter exists to keep the service up, and
            // refusing everything because the limiter is down defeats that.
            log.warn("Rate limiter unavailable, allowing the request: {{}}", ex.getMessage());
            return RateLimitDecision.unlimited();
        }}
    }}
}}
'''


FILTER = '''package com.smartseason.{pkg}.ratelimit;

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

/**
 * Counts a caller's requests and refuses the ones over the allowance.
 *
 * Placed after authentication so the key is the person, not the address. The
 * tenant is part of the key as well, so one busy organisation cannot spend
 * another's allowance.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {{

    private static final String SERVICE = "{service}";

    private final TokenBucketLimiter limiter;
    private final RateLimitProperties properties;

    public RateLimitFilter(TokenBucketLimiter limiter, RateLimitProperties properties) {{
        this.limiter = limiter;
        this.properties = properties;
    }}

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {{
        if (!properties.isEnabled()) {{
            return true;
        }}
        String path = request.getRequestURI();
        // Health and metrics are polled by the platform itself on a fixed
        // schedule; counting them would spend a caller's allowance on Kubernetes.
        return path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }}

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {{
        boolean write = isWrite(request.getMethod());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());

        String key;
        int capacity;
        double refill;

        if (authenticated) {{
            key = "rl:" + SERVICE + ":" + (write ? "w" : "r") + ":" + tenant() + ":" + authentication.getName();
            capacity = write ? properties.getWriteCapacity() : properties.getReadCapacity();
            refill = write ? properties.getWriteRefillPerSecond() : properties.getReadRefillPerSecond();
        }} else {{
            key = "rl:" + SERVICE + ":anon:" + clientAddress(request);
            capacity = properties.getAnonymousCapacity();
            refill = properties.getAnonymousRefillPerSecond();
        }}

        RateLimitDecision decision = limiter.take(key, capacity, refill);

        if (decision.remaining() >= 0) {{
            response.setHeader("X-RateLimit-Limit", Integer.toString(capacity));
            response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        }}

        if (!decision.allowed()) {{
            long seconds = Math.max(1, decision.retryAfterMillis() / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(seconds));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {{"type":"https://docs.smartseason.io/errors/rate-limited",\\
"title":"rate-limited","status":429,\\
"detail":"Too many requests. Try again in %d second(s).",\\
"code":"rate-limited"}}""".formatted(seconds));
            return;
        }}

        chain.doFilter(request, response);
    }}

    private static boolean isWrite(String method) {{
        return HttpMethod.POST.matches(method)
                || HttpMethod.PUT.matches(method)
                || HttpMethod.PATCH.matches(method)
                || HttpMethod.DELETE.matches(method);
    }}

    private static String tenant() {{
        return com.smartseason.{pkg}.platform.TenantContext.tenantId()
                .map(java.util.UUID::toString)
                .orElse("none");
    }}

    /** The caller's address, preferring what the gateway forwarded. */
    private static String clientAddress(HttpServletRequest request) {{
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {{
            return forwarded.split(",")[0].trim();
        }}
        return request.getRemoteAddr();
    }}
}}
'''
