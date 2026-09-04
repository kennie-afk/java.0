package com.kenyarealestate.notification.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
public class DomainEventConsumer {

    private final EventRouter router;

    public DomainEventConsumer(EventRouter router) {
        this.router = router;
    }

    @KafkaListener(
            topics = {"verification-events", "payment-events", "property-events", "viewing-events", "pms-events"},
            groupId = "notification-service")
    public void consume(@Payload String payload,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(name = "X-Correlation-Id", required = false) byte[] correlationIdBytes) {

        String traceId = correlationIdBytes != null
                ? new String(correlationIdBytes, StandardCharsets.UTF_8)
                : UUID.randomUUID().toString();
        org.slf4j.MDC.put("traceId", traceId);

        try {
            router.route(payload, topic);
        } catch (Exception e) {
            log.error("Failed to route event from topic {}: {}", topic, e.getMessage(), e);
            throw new RuntimeException("Event routing failed for topic " + topic, e);
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
    }
}
