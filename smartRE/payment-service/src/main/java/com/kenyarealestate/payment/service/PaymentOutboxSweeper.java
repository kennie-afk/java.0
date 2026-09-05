package com.kenyarealestate.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.payment.entity.PaymentOutboxEvent;
import com.kenyarealestate.payment.kafka.Events;
import com.kenyarealestate.payment.kafka.PaymentEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class PaymentOutboxSweeper {

    private static final int ALERT_AFTER_ATTEMPTS = 10;

    private final PaymentOutboxService outboxService;
    private final PaymentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public PaymentOutboxSweeper(PaymentOutboxService outboxService, PaymentEventPublisher eventPublisher,
                                 ObjectMapper objectMapper) {
        this.outboxService = outboxService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void sweep() {
        for (PaymentOutboxEvent row : outboxService.findUnpublishedOlderThan(Duration.ofSeconds(5))) {
            try {
                if (row.getAttempts() >= ALERT_AFTER_ATTEMPTS) {
                    log.error("ALERT: outbox event id={} paymentId={} type={} has failed to publish after {} " +
                                    "attempts. Manual intervention may be required (check Kafka broker health). " +
                                    "lastError={}",
                            row.getId(), row.getPaymentId(), row.getEventType(), row.getAttempts(), row.getLastError());
                }
                Events.PaymentCompletedEvent event =
                        objectMapper.readValue(row.getPayload(), Events.PaymentCompletedEvent.class);
                log.info("Retrying unpublished outbox event id={} paymentId={} attempts={}",
                        row.getId(), row.getPaymentId(), row.getAttempts());
                eventPublisher.sendToKafka(row.getId(), event);
            } catch (Exception e) {
                log.error("Outbox sweeper failed to process row id={}: {}", row.getId(), e.getMessage(), e);
            }
        }
    }
}
