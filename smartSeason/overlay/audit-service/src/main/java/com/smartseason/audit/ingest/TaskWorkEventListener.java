package com.smartseason.audit.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartseason.audit.chain.AppendRequest;
import com.smartseason.audit.chain.AuditAppendService;
import com.smartseason.audit.platform.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Appends an audit entry for every task start/stop, straight from the domain
 * event task-service publishes to its outbox - not from the web layer calling
 * the audit API as a side effect. A direct API call that bypasses the browser
 * entirely now audits itself, because the source of truth is the event, not a
 * client remembering to report it.
 *
 * Always goes through AuditAppendService, never a raw insert: that is what
 * assigns the sequence and the hash, and a chain whose writer picks its own
 * position proves nothing. See fraud-service's ClockEventListener for the same
 * shape of consumer.
 */
@Component
public class TaskWorkEventListener {

    private static final Logger log = LoggerFactory.getLogger(TaskWorkEventListener.class);

    private final AuditAppendService audit;
    private final ObjectMapper objectMapper;

    public TaskWorkEventListener(AuditAppendService audit, ObjectMapper objectMapper) {
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {
                "${smartseason.events.topic-prefix:ss}.task.task-started.v1",
                "${smartseason.events.topic-prefix:ss}.task.task-stopped.v1"
            },
            containerFactory = "jsonStringListenerFactory")
    public void onTaskWorkEvent(String message, Acknowledgment acknowledgment) {
        try {
            TaskWorkEnvelope envelope = objectMapper.readValue(message, TaskWorkEnvelope.class);
            var tenantId = envelope.tenantId();
            var payload = envelope.payload();

            if (tenantId == null || payload == null || payload.assignmentId() == null) {
                log.warn("Discarding task work event {} with missing tenant or payload", envelope.eventId());
                acknowledgment.acknowledge();
                return;
            }

            String action = action(envelope.eventType());
            if (action == null) {
                log.warn("Discarding task work event with unrecognised type {}", envelope.eventType());
                acknowledgment.acknowledge();
                return;
            }

            TenantContext.set(tenantId);
            try {
                audit.append(new AppendRequest(
                        "task-service",
                        payload.actorUserId(),
                        payload.actorRole(),
                        action,
                        "TaskAssignment",
                        payload.assignmentId().toString(),
                        "SUCCESS",
                        payload.occurredAt() != null ? payload.occurredAt() : envelope.occurredAt(),
                        null,
                        null,
                        null));
            } finally {
                TenantContext.clear();
            }

            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process task work event; acknowledging to avoid a poison-pill loop", ex);
            acknowledgment.acknowledge();
        }
    }

    private static String action(String eventType) {
        if ("TaskStarted".equals(eventType)) {
            return "TASK_STARTED";
        }
        if ("TaskStopped".equals(eventType)) {
            return "TASK_STOPPED";
        }
        return null;
    }
}
