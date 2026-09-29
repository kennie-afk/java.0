package com.smartseason.audit.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskWorkEnvelope(
        UUID eventId,
        String eventType,
        UUID tenantId,
        UUID aggregateId,
        Instant occurredAt,
        String producer,
        Payload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID assignmentId,
            UUID workerUserId,
            UUID actorUserId,
            String actorRole,
            Instant occurredAt) {
    }
}
