package com.kenyarealestate.verification.kafka;

import com.kenyarealestate.verification.entity.VerificationOutboxEvent;
import com.kenyarealestate.verification.service.VerificationOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publishes verification events through a transactional outbox.
 *
 * <p>The row is written inside the caller's transaction and the Kafka send is deferred
 * until that transaction commits. A rollback therefore takes the event with it, and a
 * commit followed by a failed send leaves a durable row for
 * {@link com.kenyarealestate.verification.service.VerificationOutboxSweeper} to retry.
 *
 * <p>Partition keys differ by event type — identity events are keyed by seller, ownership
 * events by property — so the key is stored alongside the payload rather than derived
 * again at retry time.
 */
@Slf4j
@Component
public class VerificationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final VerificationOutboxService outboxService;

    @Value("${kafka.topics.verification-events:verification-events}")
    private String topic;

    public VerificationEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                      VerificationOutboxService outboxService) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxService = outboxService;
    }

    public UUID publishIdentityApproved(UUID sellerId, UUID verificationId,
                                        LocalDateTime approvedAt, LocalDateTime expiresAt) {
        var event = Events.VerificationApprovedEvent.builder()
                .eventType("IDENTITY_APPROVED")
                .sellerId(sellerId)
                .verificationId(verificationId)
                .verificationType("IDENTITY")
                .approvedAt(approvedAt)
                .expiresAt(expiresAt)
                .build();
        return record(verificationId, "IDENTITY_APPROVED", sellerId.toString(), event);
    }

    public UUID publishOwnershipApproved(UUID sellerId, UUID verificationId,
                                         UUID propertyId, LocalDateTime approvedAt,
                                         String parcelNumber, String titleDeedNumber) {
        var event = Events.VerificationApprovedEvent.builder()
                .eventType("OWNERSHIP_APPROVED")
                .sellerId(sellerId)
                .verificationId(verificationId)
                .verificationType("OWNERSHIP")
                .propertyId(propertyId)
                .approvedAt(approvedAt)
                .parcelNumber(parcelNumber)
                .titleDeedNumber(titleDeedNumber)
                .build();
        return record(verificationId, "OWNERSHIP_APPROVED", propertyId.toString(), event);
    }

    private UUID record(UUID verificationId, String eventType, String messageKey,
                        Events.VerificationApprovedEvent event) {
        VerificationOutboxEvent outbox =
                outboxService.recordPending(verificationId, eventType, topic, messageKey, event);
        dispatchAfterCommit(outbox.getId(), messageKey, event);
        return outbox.getId();
    }

    private void dispatchAfterCommit(UUID outboxEventId, String messageKey,
                                     Events.VerificationApprovedEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendToKafka(outboxEventId, messageKey, event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendToKafka(outboxEventId, messageKey, event);
            }
        });
    }

    public void sendToKafka(UUID outboxEventId, String messageKey,
                            Events.VerificationApprovedEvent event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, messageKey, event);
        attachTraceHeader(record);

        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for verificationId={} (outboxId={}): {}",
                                event.getEventType(), event.getVerificationId(), outboxEventId, ex.getMessage());
                        outboxService.markAttemptFailed(outboxEventId, ex.getMessage());
                    } else {
                        log.info("Published {} verificationId={} key={} (outboxId={})",
                                event.getEventType(), event.getVerificationId(), messageKey, outboxEventId);
                        outboxService.markPublished(outboxEventId);
                    }
                });
    }

    private void attachTraceHeader(ProducerRecord<String, Object> record) {
        String correlationId = MDC.get("traceId");
        if (!StringUtils.hasText(correlationId)) correlationId = UUID.randomUUID().toString();
        record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));
    }
}
