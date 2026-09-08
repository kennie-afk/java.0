package com.kenyarealestate.verification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.verification.entity.VerificationOutboxEvent;
import com.kenyarealestate.verification.repository.VerificationOutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reads and writes the verification outbox.
 *
 * <p>{@code recordPending} joins the caller's transaction so the row and the approval it
 * describes commit together. The two mark methods run in their own transaction, because
 * they are invoked from a Kafka producer callback after the original transaction has
 * already closed.
 */
@Slf4j
@Service
public class VerificationOutboxService {

    private final VerificationOutboxEventRepository repo;
    private final ObjectMapper objectMapper;

    public VerificationOutboxService(VerificationOutboxEventRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public VerificationOutboxEvent recordPending(UUID verificationId, String eventType, String topic,
                                                 String messageKey, Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize outbox event for verificationId=" + verificationId, e);
        }
        return repo.save(VerificationOutboxEvent.builder()
                .verificationId(verificationId)
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
    public List<VerificationOutboxEvent> findUnpublishedOlderThan(Duration grace) {
        return repo.findByPublishedFalseAndCreatedAtBefore(LocalDateTime.now().minus(grace));
    }
}
