package com.kenyarealestate.verification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.verification.entity.VerificationOutboxEvent;
import com.kenyarealestate.verification.kafka.Events;
import com.kenyarealestate.verification.kafka.VerificationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Retries verification events that were never acknowledged as published.
 *
 * <p>At-least-once: consumers of verification-events must tolerate a repeat. Approving a
 * seller twice is idempotent; failing to approve them at all is not recoverable without
 * someone noticing by hand.
 */
@Slf4j
@Component
public class VerificationOutboxSweeper {

    private static final int ALERT_AFTER_ATTEMPTS = 10;
    private static final Duration GRACE = Duration.ofSeconds(5);

    private final VerificationOutboxService outboxService;
    private final VerificationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public VerificationOutboxSweeper(VerificationOutboxService outboxService,
                                     VerificationEventPublisher eventPublisher,
                                     ObjectMapper objectMapper) {
        this.outboxService = outboxService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void sweep() {
        for (VerificationOutboxEvent row : outboxService.findUnpublishedOlderThan(GRACE)) {
            try {
                if (row.getAttempts() >= ALERT_AFTER_ATTEMPTS) {
                    log.error("ALERT: outbox event id={} verificationId={} type={} has failed to publish after " +
                                    "{} attempts. Manual intervention may be required (check Kafka broker " +
                                    "health). lastError={}",
                            row.getId(), row.getVerificationId(), row.getEventType(),
                            row.getAttempts(), row.getLastError());
                }
                Events.VerificationApprovedEvent event =
                        objectMapper.readValue(row.getPayload(), Events.VerificationApprovedEvent.class);
                log.info("Retrying unpublished outbox event id={} verificationId={} attempts={}",
                        row.getId(), row.getVerificationId(), row.getAttempts());
                eventPublisher.sendToKafka(row.getId(), row.getMessageKey(), event);
            } catch (Exception e) {
                log.error("Outbox sweeper failed to process row id={}: {}", row.getId(), e.getMessage(), e);
            }
        }
    }
}
