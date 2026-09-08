package com.smartseason.marketplace.platform;

import java.time.Duration;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class CountCache {

    private static final Logger log = LoggerFactory.getLogger(CountCache.class);
    private static final String SERVICE = "marketplace-service";

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final boolean enabled;

    public CountCache(StringRedisTemplate redis,
                      @Value("${smartseason.cache.count-ttl-seconds:30}") long ttlSeconds,
                      @Value("${smartseason.cache.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.enabled = enabled;
    }

    public long total(String entity, UUID tenantId, LongSupplier loader) {
        if (!enabled) {
            return loader.getAsLong();
        }

        String key = key(entity, tenantId);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return Long.parseLong(cached);
            }
        } catch (RuntimeException ex) {
            log.debug("Count cache unavailable, counting in the database: {}", ex.getMessage());
            return loader.getAsLong();
        }

        long counted = loader.getAsLong();
        try {
            redis.opsForValue().set(key, Long.toString(counted), ttl);
        } catch (RuntimeException ex) {
            log.debug("Could not store a count: {}", ex.getMessage());
        }
        return counted;
    }

    public void invalidate(String entity, UUID tenantId) {
        if (!enabled) {
            return;
        }
        try {
            redis.delete(key(entity, tenantId));
        } catch (RuntimeException ex) {
            log.debug("Could not clear a count: {}", ex.getMessage());
        }
    }

    private static String key(String entity, UUID tenantId) {
        return "cnt:" + SERVICE + ":" + entity + ":" + tenantId;
    }
}
