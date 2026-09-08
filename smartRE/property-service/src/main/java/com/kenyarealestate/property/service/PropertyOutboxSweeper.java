package com.kenyarealestate.property.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.property.entity.PropertyOutboxEvent;
import com.kenyarealestate.property.kafka.Events;
import com.kenyarealestate.property.kafka.PropertyEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Retries property events that were never acknowledged as published.
 *
 * <p>Event types resolve through the fixed map below rather than a stored class name: the
 * payload column is data, and turning a string from a database row into a class to
 * instantiate is how deserialization bugs become remote code execution.
 */
@Slf4j
@Component
public class PropertyOutboxSweeper {

    private static final int ALERT_AFTER_ATTEMPTS = 10;
    private static final Duration GRACE = Duration.ofSeconds(5);

    private static final Map<String, Class<?>> EVENT_TYPES = Map.of(
            "LISTINGS_SUSPENDED", Events.ListingsSuspendedEvent.class);

    private final PropertyOutboxService outboxService;
    private final PropertyEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public PropertyOutboxSweeper(PropertyOutboxService outboxService,
                                 PropertyEventPublisher eventPublisher,
                                 ObjectMapper objectMapper) {
        this.outboxService = outboxService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void sweep() {
        for (PropertyOutboxEvent row : outboxService.findUnpublishedOlderThan(GRACE)) {
            try {
                if (row.getAttempts() >= ALERT_AFTER_ATTEMPTS) {
                    log.error("ALERT: outbox event id={} sellerId={} type={} has failed to publish after {} " +
                                    "attempts. Manual intervention may be required. lastError={}",
                            row.getId(), row.getSellerId(), row.getEventType(),
                            row.getAttempts(), row.getLastError());
                }
                Class<?> type = EVENT_TYPES.get(row.getEventType());
                if (type == null) {
                    log.error("Outbox row id={} has unknown event type '{}' — leaving it unpublished rather " +
                            "than guessing at its shape", row.getId(), row.getEventType());
                    continue;
                }
                Object event = objectMapper.readValue(row.getPayload(), type);
                eventPublisher.sendToKafka(row.getId(), row.getMessageKey(), row.getEventType(), event);
            } catch (Exception e) {
                log.error("Outbox sweeper failed to process row id={}: {}", row.getId(), e.getMessage(), e);
            }
        }
    }
}
