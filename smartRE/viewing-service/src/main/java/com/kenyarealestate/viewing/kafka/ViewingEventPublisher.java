package com.kenyarealestate.viewing.kafka;

import com.kenyarealestate.viewing.entity.Viewing;
import com.kenyarealestate.viewing.entity.ViewingOutboxEvent;
import com.kenyarealestate.viewing.service.ViewingOutboxService;
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
 * Publishes viewing events through a transactional outbox.
 *
 * <p>The ordering is the whole point. {@code recordAndPublish} writes the outbox row
 * inside the caller's transaction, then defers the Kafka send until after that
 * transaction commits. So:
 *
 * <ul>
 *   <li>Transaction rolls back — the row vanishes with it and nothing is ever sent. No
 *       event for a viewing that was not actually completed.</li>
 *   <li>Transaction commits, send fails — the row survives, unpublished, and
 *       {@link com.kenyarealestate.viewing.service.ViewingOutboxSweeper} retries it.</li>
 *   <li>Process dies between commit and send — same as above. The row is the record of
 *       intent; the send is only an optimisation over waiting for the sweeper.</li>
 * </ul>
 */
@Slf4j
@Component
public class ViewingEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ViewingOutboxService outboxService;

    @Value("${kafka.topics.viewing-events:viewing-events}")
    private String topic;

    public ViewingEventPublisher(KafkaTemplate<String, Object> kafkaTemplate, ViewingOutboxService outboxService) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxService = outboxService;
    }

    public Events.ViewingCompletedEvent buildViewingCompletedEvent(Viewing viewing) {
        return Events.ViewingCompletedEvent.builder()
                .eventType("VIEWING_COMPLETED")
                .viewingId(viewing.getId())
                .propertyId(viewing.getPropertyId())
                .buyerId(viewing.getBuyerId())
                .sellerId(viewing.getSellerId())
                .completedAt(LocalDateTime.now())
                .build();
    }

    public UUID recordAndPublish(Viewing viewing) {
        Events.ViewingCompletedEvent event = buildViewingCompletedEvent(viewing);
        ViewingOutboxEvent outbox =
                outboxService.recordPending(viewing.getId(), "VIEWING_COMPLETED", topic, event);
        dispatchAfterCommit(outbox.getId(), event);
        return outbox.getId();
    }

    private void dispatchAfterCommit(UUID outboxEventId, Events.ViewingCompletedEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // No surrounding transaction — the row is already committed, so send now.
            sendToKafka(outboxEventId, event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendToKafka(outboxEventId, event);
            }
        });
    }

    public void sendToKafka(UUID outboxEventId, Events.ViewingCompletedEvent event) {
        ProducerRecord<String, Object> record =
                new ProducerRecord<>(topic, event.getPropertyId().toString(), event);
        String correlationId = MDC.get("traceId");
        if (!StringUtils.hasText(correlationId)) correlationId = UUID.randomUUID().toString();
        record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish VIEWING_COMPLETED for viewingId={} (outboxId={}): {}",
                                event.getViewingId(), outboxEventId, ex.getMessage());
                        outboxService.markAttemptFailed(outboxEventId, ex.getMessage());
                    } else {
                        log.info("Published VIEWING_COMPLETED viewingId={} (outboxId={})",
                                event.getViewingId(), outboxEventId);
                        outboxService.markPublished(outboxEventId);
                    }
                });
    }
}
