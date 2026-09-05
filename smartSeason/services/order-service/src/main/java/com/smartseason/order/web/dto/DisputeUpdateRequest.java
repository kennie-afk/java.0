package com.smartseason.order.web.dto;

import com.smartseason.order.domain.Dispute;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record DisputeUpdateRequest(
        UUID orderId,
        UUID raisedByOrgId,
        Dispute.Category category,
        String description,
        Instant raisedAt,
        Dispute.Status status,
        String resolution,
        Instant resolvedAt,
        UUID resolvedBy) {
}
