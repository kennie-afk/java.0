package com.smartseason.order.web.dto;

import com.smartseason.order.domain.Dispute;
import java.time.Instant;
import java.util.UUID;

public record DisputeResponse(
        UUID id,
        UUID orderId,
        UUID raisedByOrgId,
        Dispute.Category category,
        String description,
        Instant raisedAt,
        Dispute.Status status,
        String resolution,
        Instant resolvedAt,
        UUID resolvedBy,
        Instant createdAt,
        Instant updatedAt) {

    public static DisputeResponse from(Dispute entity) {
        return new DisputeResponse(
                entity.getId(),
                entity.getOrderId(),
                entity.getRaisedByOrgId(),
                entity.getCategory(),
                entity.getDescription(),
                entity.getRaisedAt(),
                entity.getStatus(),
                entity.getResolution(),
                entity.getResolvedAt(),
                entity.getResolvedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
