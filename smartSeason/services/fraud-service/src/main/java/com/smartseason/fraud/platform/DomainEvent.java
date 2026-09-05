package com.smartseason.fraud.platform;

import java.time.Instant;
import java.util.UUID;

public record DomainEvent<T>(
        UUID eventId,
        String eventType,
        int version,
        UUID tenantId,
        UUID aggregateId,
        Instant occurredAt,
        String producer,
        T payload) {

    public static <T> DomainEvent<T> of(String eventType, UUID aggregateId, String producer, T payload) {
        return new DomainEvent<>(
                UUID.randomUUID(),
                eventType,
                1,
                TenantContext.tenantId().orElse(null),
                aggregateId,
                Instant.now(),
                producer,
                payload);
    }
}
