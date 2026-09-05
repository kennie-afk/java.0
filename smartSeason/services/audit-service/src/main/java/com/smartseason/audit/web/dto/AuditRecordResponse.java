package com.smartseason.audit.web.dto;

import com.smartseason.audit.domain.AuditRecord;
import java.time.Instant;
import java.util.UUID;

public record AuditRecordResponse(
        UUID id,
        Long sequence,
        String serviceName,
        UUID actorUserId,
        String actorRole,
        String action,
        String resourceType,
        String resourceId,
        AuditRecord.Outcome outcome,
        Instant occurredAt,
        String ipAddress,
        String userAgent,
        String details,
        String previousHash,
        String recordHash,
        Instant createdAt,
        Instant updatedAt) {

    public static AuditRecordResponse from(AuditRecord entity) {
        return new AuditRecordResponse(
                entity.getId(),
                entity.getSequence(),
                entity.getServiceName(),
                entity.getActorUserId(),
                entity.getActorRole(),
                entity.getAction(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getOutcome(),
                entity.getOccurredAt(),
                entity.getIpAddress(),
                entity.getUserAgent(),
                entity.getDetails(),
                entity.getPreviousHash(),
                entity.getRecordHash(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
