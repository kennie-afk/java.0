package com.kenyarealestate.viewing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.viewing.entity.ViewingOutboxEvent;
import com.kenyarealestate.viewing.kafka.Events;
import com.kenyarealestate.viewing.kafka.ViewingEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Retries outbox rows that were never acknowledged as published.
 *
 * <p>This is an at-least-once channel, not exactly-once: a send that reached the broker
 * but whose acknowledgement was lost will be retried, and consumers of viewing-events
 * must be idempotent. That is the correct trade — a duplicate VIEWING_COMPLETED is
 * harmless, a missing one silently denies the buyer their review.
 */
@Slf4j
@Component
public class ViewingOutboxSweeper {

    private static final int ALERT_AFTER_ATTEMPTS = 10;
    private static final Duration GRACE = Duration.ofSeconds(5);

    private final ViewingOutboxService outboxService;
    private final ViewingEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public ViewingOutboxSweeper(ViewingOutboxService outboxService, ViewingEventPublisher eventPublisher,
                                ObjectMapper objectMapper) {
        this.outboxService = outboxService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void sweep() {
        for (ViewingOutboxEvent row : outboxService.findUnpublishedOlderThan(GRACE)) {
            try {
                if (row.getAttempts() >= ALERT_AFTER_ATTEMPTS) {
                    log.error("ALERT: outbox event id={} viewingId={} type={} has failed to publish after {} " +
                                    "attempts. Manual intervention may be required (check Kafka broker health). " +
                                    "lastError={}",
                            row.getId(), row.getViewingId(), row.getEventType(), row.getAttempts(), row.getLastError());
                }
                Events.ViewingCompletedEvent event =
                        objectMapper.readValue(row.getPayload(), Events.ViewingCompletedEvent.class);
                log.info("Retrying unpublished outbox event id={} viewingId={} attempts={}",
                        row.getId(), row.getViewingId(), row.getAttempts());
                eventPublisher.sendToKafka(row.getId(), event);
            } catch (Exception e) {
                // One malformed row must not stop the rest of the sweep.
                log.error("Outbox sweeper failed to process row id={}: {}", row.getId(), e.getMessage(), e);
            }
        }
    }
}
