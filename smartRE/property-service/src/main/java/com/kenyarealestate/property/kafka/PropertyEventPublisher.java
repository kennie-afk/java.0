package com.kenyarealestate.property.kafka;

import com.kenyarealestate.property.entity.PropertyOutboxEvent;
import com.kenyarealestate.property.service.PropertyOutboxService;
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
 * Publishes property events through a transactional outbox.
 *
 * <p>The row is written inside the caller's transaction and the Kafka send deferred until
 * that transaction commits, so a rollback takes the event with it and a failed send
 * leaves a durable row for the sweeper. This is the first producer property-service has
 * ever had; the topic and the consumer on the other end already existed.
 */
@Slf4j
@Component
public class PropertyEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PropertyOutboxService outboxService;

    @Value("${kafka.topics.property-events:property-events}")
    private String topic;

    public PropertyEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                  PropertyOutboxService outboxService) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxService = outboxService;
    }

    public UUID publishListingsSuspended(UUID sellerId, int suspendedCount, String reason) {
        Events.ListingsSuspendedEvent event = Events.ListingsSuspendedEvent.builder()
                .eventType("LISTINGS_SUSPENDED")
                .sellerId(sellerId)
                .suspendedCount(suspendedCount)
                .reason(reason)
                .suspendedAt(LocalDateTime.now())
                .build();

        PropertyOutboxEvent outbox = outboxService.recordPending(
                sellerId, "LISTINGS_SUSPENDED", topic, sellerId.toString(), event);
        dispatchAfterCommit(outbox.getId(), sellerId.toString(), "LISTINGS_SUSPENDED", event);
        return outbox.getId();
    }

    private void dispatchAfterCommit(UUID outboxEventId, String key, String eventType, Object event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendToKafka(outboxEventId, key, eventType, event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendToKafka(outboxEventId, key, eventType, event);
            }
        });
    }

    public void sendToKafka(UUID outboxEventId, String key, String eventType, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        String correlationId = MDC.get("correlationId");
        if (!StringUtils.hasText(correlationId)) correlationId = UUID.randomUUID().toString();
        record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} for sellerId={} (outboxId={}): {}",
                        eventType, key, outboxEventId, ex.getMessage());
                outboxService.markAttemptFailed(outboxEventId, ex.getMessage());
            } else {
                log.info("Published {} for sellerId={} (outboxId={})", eventType, key, outboxEventId);
                outboxService.markPublished(outboxEventId);
            }
        });
    }
}
