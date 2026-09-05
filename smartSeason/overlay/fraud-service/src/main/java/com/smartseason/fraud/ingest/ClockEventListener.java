package com.smartseason.fraud.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartseason.fraud.platform.TenantContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class ClockEventListener {

    private static final Logger log = LoggerFactory.getLogger(ClockEventListener.class);

    private final FraudIngestService ingest;
    private final ObjectMapper objectMapper;

    public ClockEventListener(FraudIngestService ingest, ObjectMapper objectMapper) {
        this.ingest = ingest;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${smartseason.events.topic-prefix:ss}.workforce.clock-event-created.v1",
            containerFactory = "jsonStringListenerFactory")
    public void onClockEvent(String message, Acknowledgment acknowledgment) {
        try {
            ClockEventEnvelope envelope = objectMapper.readValue(message, ClockEventEnvelope.class);
            UUID tenantId = envelope.tenantId();

            if (tenantId == null) {
                log.warn("Discarding clock event {} with no tenant", envelope.eventId());
                acknowledgment.acknowledge();
                return;
            }

            TenantContext.set(tenantId);
            try {
                ingest.ingestClockEvent(tenantId, envelope.payload());
            } finally {
                TenantContext.clear();
            }

            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process clock event; acknowledging to avoid a poison-pill loop", ex);
            acknowledgment.acknowledge();
        }
    }
}
