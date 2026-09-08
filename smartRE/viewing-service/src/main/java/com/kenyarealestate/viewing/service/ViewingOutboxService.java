package com.kenyarealestate.viewing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.viewing.entity.ViewingOutboxEvent;
import com.kenyarealestate.viewing.repository.ViewingOutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reads and writes the outbox table. The transaction annotations here carry the whole
 * design, so they are worth stating plainly:
 *
 * <p>{@code recordPending} joins whatever transaction the caller is already in — that is
 * the point. The outbox row and the business change it describes commit together or not
 * at all.
 *
 * <p>{@code markPublished} and {@code markAttemptFailed} run in their own transaction
 * ({@code REQUIRES_NEW}), because they are called from a Kafka callback on a producer
 * thread long after the original transaction closed. Joining a transaction that no
 * longer exists would silently open a new one per call anyway; saying so explicitly
 * makes the intent readable and survives being called from inside a live transaction.
 */
@Slf4j
@Service
public class ViewingOutboxService {

    private final ViewingOutboxEventRepository repo;
    private final ObjectMapper objectMapper;

    public ViewingOutboxService(ViewingOutboxEventRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ViewingOutboxEvent recordPending(UUID viewingId, String eventType, String topic, Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Deliberately fatal: an event we cannot serialize is one we can never
            // deliver, and failing here rolls back the business change rather than
            // completing it with no downstream notification.
            throw new IllegalStateException("Failed to serialize outbox event for viewingId=" + viewingId, e);
        }
        return repo.save(ViewingOutboxEvent.builder()
                .viewingId(viewingId)
                .eventType(eventType)
                .topic(topic)
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

    /**
     * The grace period matters: a row created moments ago may simply be in flight on its
     * first attempt. Sweeping it immediately would double-publish for no reason.
     */
    @Transactional(readOnly = true)
    public List<ViewingOutboxEvent> findUnpublishedOlderThan(Duration grace) {
        return repo.findByPublishedFalseAndCreatedAtBefore(LocalDateTime.now().minus(grace));
    }
}
