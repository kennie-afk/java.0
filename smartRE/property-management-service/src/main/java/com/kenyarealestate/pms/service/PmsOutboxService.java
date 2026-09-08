package com.kenyarealestate.pms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.pms.entity.PmsOutboxEvent;
import com.kenyarealestate.pms.repository.PmsOutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reads and writes the property-management outbox.
 *
 * <p>{@code recordPending} joins the caller's transaction so the row and the rent or
 * maintenance change it describes commit together. The two mark methods run in their own
 * transaction, because they are invoked from a Kafka producer callback after the
 * original transaction has already closed.
 */
@Slf4j
@Service
public class PmsOutboxService {

    private final PmsOutboxEventRepository repo;
    private final ObjectMapper objectMapper;

    public PmsOutboxService(PmsOutboxEventRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PmsOutboxEvent recordPending(UUID aggregateId, String eventType, String topic,
                                        String messageKey, Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event for aggregateId=" + aggregateId, e);
        }
        return repo.save(PmsOutboxEvent.builder()
                .aggregateId(aggregateId)
                .eventType(eventType)
                .topic(topic)
                .messageKey(messageKey)
                .payload(payload)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID outboxId) {
        repo.findById(outboxId).ifPresent(e -> {
            e.setPublished(true);
            e.setPublishedAt(LocalDateTime.now());
            repo.save(e);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAttemptFailed(UUID outboxId, String error) {
        repo.findById(outboxId).ifPresent(e -> {
            e.setAttempts(e.getAttempts() + 1);
            e.setLastError(error);
            repo.save(e);
        });
    }

    @Transactional(readOnly = true)
    public List<PmsOutboxEvent> findUnpublishedOlderThan(Duration grace) {
        return repo.findByPublishedFalseAndCreatedAtBefore(LocalDateTime.now().minus(grace));
    }
}
