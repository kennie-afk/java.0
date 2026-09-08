package com.kenyarealestate.pms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.pms.entity.PmsOutboxEvent;
import com.kenyarealestate.pms.kafka.Events;
import com.kenyarealestate.pms.kafka.PmsEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Retries property-management events that were never acknowledged as published.
 *
 * <p>Rows carry an event type rather than a class name, and the type is resolved through
 * the fixed map below. That is deliberate: the payload column is data, and turning a
 * string stored in a database row into a class to instantiate is how deserialization
 * bugs become remote code execution. A closed map cannot name a class this service did
 * not already ship.
 *
 * <p>At-least-once: consumers of pms-events must tolerate a repeat. A duplicate rent
 * notification is an annoyance; a missing one is a tenant who never learns they owe rent.
 */
@Slf4j
@Component
public class PmsOutboxSweeper {

    private static final int ALERT_AFTER_ATTEMPTS = 10;
    private static final Duration GRACE = Duration.ofSeconds(5);

    private static final Map<String, Class<?>> EVENT_TYPES = Map.of(
            "LEASE_ACTIVATED",      Events.LeaseActivatedEvent.class,
            "LEASE_ENDED",          Events.LeaseEndedEvent.class,
            "RENT_INVOICE_ISSUED",  Events.RentInvoiceIssuedEvent.class,
            "RENT_OVERDUE",         Events.RentOverdueEvent.class,
            "RENT_RECEIVED",        Events.RentReceivedEvent.class,
            "MAINTENANCE_RAISED",   Events.MaintenanceRaisedEvent.class,
            "MAINTENANCE_RESOLVED", Events.MaintenanceResolvedEvent.class);

    private final PmsOutboxService outboxService;
    private final PmsEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public PmsOutboxSweeper(PmsOutboxService outboxService, PmsEventPublisher eventPublisher,
                            ObjectMapper objectMapper) {
        this.outboxService = outboxService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void sweep() {
        for (PmsOutboxEvent row : outboxService.findUnpublishedOlderThan(GRACE)) {
            try {
                if (row.getAttempts() >= ALERT_AFTER_ATTEMPTS) {
                    log.error("ALERT: outbox event id={} aggregateId={} type={} has failed to publish after {} " +
                                    "attempts. Manual intervention may be required (check Kafka broker health). " +
                                    "lastError={}",
                            row.getId(), row.getAggregateId(), row.getEventType(),
                            row.getAttempts(), row.getLastError());
                }
                Class<?> type = EVENT_TYPES.get(row.getEventType());
                if (type == null) {
                    log.error("Outbox row id={} has unknown event type '{}' — leaving it unpublished rather " +
                            "than guessing at its shape", row.getId(), row.getEventType());
                    continue;
                }
                Object event = objectMapper.readValue(row.getPayload(), type);
                log.info("Retrying unpublished outbox event id={} type={} attempts={}",
                        row.getId(), row.getEventType(), row.getAttempts());
                eventPublisher.sendToKafka(row.getId(), row.getMessageKey(), row.getEventType(), event);
            } catch (Exception e) {
                log.error("Outbox sweeper failed to process row id={}: {}", row.getId(), e.getMessage(), e);
            }
        }
    }
}
