"""Cached row counts for list endpoints.

Every list endpoint returns a total so the caller can render "1-25 of N". Spring
Data produces that total with a second query - `SELECT count(*) ... WHERE
tenant_id = ?` - on every single request. With an index that is not a table
scan, but it still walks every matching index entry: for a tenant holding a
million rows, a million entries per page view, on a page nobody scrolls past the
second screen of.

The count is therefore cached in Redis, keyed by service, entity and tenant, and
dropped whenever that entity is written. A total may be up to `ttl` seconds stale
in the window between another replica's write and this one's eviction, which is
the right trade: nobody makes a decision on the last digit of a row count, and
the alternative is counting a million rows to render a caption.

The rows themselves are never cached - only the count. Cached rows would need
invalidating on every write and are exactly where stale-data bugs come from.
"""

COUNT_CACHE = '''package com.smartseason.{pkg}.platform;

import java.time.Duration;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Caches the row count behind each list endpoint.
 *
 * <p>Counting is the expensive half of a paged read: the page itself is bounded
 * by the page size, while the total walks every row the tenant owns. Caching it
 * turns a per-request count into one count per {{@code ttl}} per tenant.
 *
 * <p>Falls through to the database on any Redis trouble. A slow list is better
 * than a broken one.
 */
@Component
public class CountCache {{

    private static final Logger log = LoggerFactory.getLogger(CountCache.class);
    private static final String SERVICE = "{service}";

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final boolean enabled;

    public CountCache(StringRedisTemplate redis,
                      @Value("${{smartseason.cache.count-ttl-seconds:30}}") long ttlSeconds,
                      @Value("${{smartseason.cache.enabled:true}}") boolean enabled) {{
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.enabled = enabled;
    }}

    public long total(String entity, UUID tenantId, LongSupplier loader) {{
        if (!enabled) {{
            return loader.getAsLong();
        }}

        String key = key(entity, tenantId);
        try {{
            String cached = redis.opsForValue().get(key);
            if (cached != null) {{
                return Long.parseLong(cached);
            }}
        }} catch (RuntimeException ex) {{
            log.debug("Count cache unavailable, counting in the database: {{}}", ex.getMessage());
            return loader.getAsLong();
        }}

        long counted = loader.getAsLong();
        try {{
            redis.opsForValue().set(key, Long.toString(counted), ttl);
        }} catch (RuntimeException ex) {{
            log.debug("Could not store a count: {{}}", ex.getMessage());
        }}
        return counted;
    }}

    /**
     * Drops the cached count after a write.
     *
     * <p>Only this replica's write is seen here; another replica's write is
     * covered by the TTL. That is the bounded staleness this cache trades for.
     */
    public void invalidate(String entity, UUID tenantId) {{
        if (!enabled) {{
            return;
        }}
        try {{
            redis.delete(key(entity, tenantId));
        }} catch (RuntimeException ex) {{
            log.debug("Could not clear a count: {{}}", ex.getMessage());
        }}
    }}

    private static String key(String entity, UUID tenantId) {{
        return "cnt:" + SERVICE + ":" + entity + ":" + tenantId;
    }}
}}
'''


# PageResponse gains a constructor that takes a Slice plus a total, so the
# content query and the count are no longer bound together.
PAGE_RESPONSE = '''package com.smartseason.{pkg}.platform;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;

/**
 * Transport shape for a page of results.
 *
 * <p>Spring's {{@code Page}} is deliberately not serialised directly — its JSON layout is
 * an implementation detail that would otherwise become part of the public API contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {{

    public static <T> PageResponse<T> from(Page<T> page) {{
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }}

    /**
     * Builds a page from a slice and a separately obtained total.
     *
     * <p>A {{@code Slice}} does not issue a count query, which is the point: the
     * total comes from {{@link CountCache}} instead of from a fresh count on
     * every request.
     */
    public static <T> PageResponse<T> of(Slice<T> slice, long totalElements) {{
        int size = slice.getSize();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(
                slice.getContent(),
                slice.getNumber(),
                size,
                totalElements,
                totalPages,
                slice.getNumber() == 0,
                !slice.hasNext());
    }}
}}
'''
