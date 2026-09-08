package com.smartseason.farm.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class CountCacheTest {

    private static final UUID TENANT = UUID.randomUUID();

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private CountCache cache;
    private AtomicInteger databaseCounts;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        cache = new CountCache(redis, 30, true);
        databaseCounts = new AtomicInteger();
    }

    private long count() {
        return cache.total("farms", TENANT, () -> {
            databaseCounts.incrementAndGet();
            return 4_200L;
        });
    }

    @Test
    void countsInTheDatabaseOnceThenStoresTheAnswer() {
        when(values.get(anyString())).thenReturn(null);

        assertThat(count()).isEqualTo(4_200L);
        assertThat(databaseCounts.get()).isEqualTo(1);
        verify(values).set(anyString(), eq("4200"), any(java.time.Duration.class));
    }

    @Test
    void aCachedCountDoesNotTouchTheDatabase() {
        when(values.get(anyString())).thenReturn("99");

        assertThat(count()).isEqualTo(99L);
        assertThat(databaseCounts.get()).isZero();
    }

    @Test
    void keysAreSeparatePerTenant() {
        when(values.get(anyString())).thenReturn(null);
        cache.total("farms", TENANT, () -> 1L);
        cache.total("farms", UUID.randomUUID(), () -> 2L);

        var keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values, org.mockito.Mockito.times(2)).get(keys.capture());
        assertThat(keys.getAllValues().get(0)).isNotEqualTo(keys.getAllValues().get(1));
    }

    @Test
    void redisFailingFallsBackToTheDatabaseRatherThanFailingTheRequest() {
        when(values.get(anyString())).thenThrow(new RuntimeException("redis is down"));

        assertThat(count()).isEqualTo(4_200L);
        assertThat(databaseCounts.get()).isEqualTo(1);
    }

    @Test
    void aFailureToStoreStillReturnsTheCount() {
        when(values.get(anyString())).thenReturn(null);
        doThrow(new RuntimeException("redis is down"))
                .when(values).set(anyString(), anyString(), any(java.time.Duration.class));

        assertThat(count()).isEqualTo(4_200L);
    }

    @Test
    void disablingTheCacheAlwaysCountsAndNeverTouchesRedis() {
        CountCache off = new CountCache(redis, 30, false);

        assertThat(off.total("farms", TENANT, () -> {
            databaseCounts.incrementAndGet();
            return 7L;
        })).isEqualTo(7L);
        assertThat(databaseCounts.get()).isEqualTo(1);
        verify(values, never()).get(anyString());
    }

    @Test
    void invalidatingRemovesTheKeyAndSurvivesRedisBeingDown() {
        cache.invalidate("farms", TENANT);
        verify(redis).delete(anyString());

        when(redis.delete(anyString())).thenThrow(new RuntimeException("redis is down"));
        cache.invalidate("farms", TENANT);
    }
}
