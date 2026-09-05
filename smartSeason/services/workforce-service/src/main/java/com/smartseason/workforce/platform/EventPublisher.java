package com.smartseason.workforce.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final String topicPrefix;
    private final String producer;

    public EventPublisher(OutboxRepository outbox,
                          ObjectMapper objectMapper,
                          @Value("${smartseason.events.topic-prefix:ss}") String topicPrefix,
                          @Value("${spring.application.name}") String producer) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.topicPrefix = topicPrefix;
        this.producer = producer;
    }

    public void publish(String domain, String type, UUID aggregateId, Object payload) {
        DomainEvent<Object> event = DomainEvent.of(type, aggregateId, producer, payload);
        String topic = "%s.%s.%s.v1".formatted(topicPrefix, domain, camelToKebab(type));

        OutboxEntry entry = new OutboxEntry();
        entry.setId(UUID.randomUUID());
        entry.setTenantId(TenantContext.tenantId().orElse(null));
        entry.setTopic(topic);
        entry.setMessageKey(aggregateId == null ? null : aggregateId.toString());
        entry.setPayload(serialise(event));
        entry.setEventType(type);
        entry.setStatus(OutboxEntry.Status.PENDING);
        entry.setAttempts(0);
        entry.setCreatedAt(Instant.now());

        outbox.save(entry);
    }

    private String serialise(DomainEvent<Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise event " + event.eventType(), ex);
        }
    }

    private static String camelToKebab(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }
}
