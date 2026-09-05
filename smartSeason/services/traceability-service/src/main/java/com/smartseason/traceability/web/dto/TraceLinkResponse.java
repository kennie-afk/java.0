package com.smartseason.traceability.web.dto;

import com.smartseason.traceability.domain.TraceLink;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TraceLinkResponse(
        UUID id,
        String batchCode,
        Integer sequence,
        TraceLink.NodeType nodeType,
        String nodeRef,
        Instant occurredAt,
        UUID actorOrgId,
        String location,
        BigDecimal latitude,
        BigDecimal longitude,
        String attributes,
        String evidenceUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static TraceLinkResponse from(TraceLink entity) {
        return new TraceLinkResponse(
                entity.getId(),
                entity.getBatchCode(),
                entity.getSequence(),
                entity.getNodeType(),
                entity.getNodeRef(),
                entity.getOccurredAt(),
                entity.getActorOrgId(),
                entity.getLocation(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getAttributes(),
                entity.getEvidenceUrl(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
