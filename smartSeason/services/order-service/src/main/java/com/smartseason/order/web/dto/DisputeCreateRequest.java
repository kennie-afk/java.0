package com.smartseason.order.web.dto;

import com.smartseason.order.domain.Dispute;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record DisputeCreateRequest(
        @NotNull UUID orderId,
        @NotNull UUID raisedByOrgId,
        @NotNull Dispute.Category category,
        @NotBlank String description,
        @NotNull Instant raisedAt,
        @NotNull Dispute.Status status,
        String resolution,
        Instant resolvedAt,
        UUID resolvedBy) {
}
