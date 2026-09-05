package com.smartseason.audit.platform;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int MAX_ATTEMPTS = 10;

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final boolean enabled;
    private final int batchSize;

    public OutboxRelay(OutboxRepository outbox,
                       KafkaTemplate<String, Object> kafka,
                       @Value("${smartseason.events.enabled:false}") boolean enabled,
                       @Value("${smartseason.events.relay-batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${smartseason.events.relay-interval-ms:2000}")
    @Transactional
    public void relay() {
        if (!enabled) {
            return;
        }

        List<OutboxEntry> pending = outbox.findAllByStatusOrderByCreatedAtAsc(
                OutboxEntry.Status.PENDING, PageRequest.of(0, batchSize));

        for (OutboxEntry entry : pending) {
            try {
                kafka.send(entry.getTopic(), entry.getMessageKey(), entry.getPayload()).get();
                entry.setStatus(OutboxEntry.Status.PUBLISHED);
                entry.setPublishedAt(Instant.now());
            } catch (Exception ex) {
                Thread.currentThread().interrupt();
                entry.setAttempts(entry.getAttempts() + 1);
                entry.setLastError(ex.getMessage());
                if (entry.getAttempts() >= MAX_ATTEMPTS) {
                    entry.setStatus(OutboxEntry.Status.FAILED);
                    log.error("Outbox entry {} exhausted {} attempts and is parked for replay",
                            entry.getId(), MAX_ATTEMPTS);
                }
            }
            outbox.save(entry);
        }
    }
}
