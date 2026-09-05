package com.smartseason.order.web.dto;

import com.smartseason.order.domain.OrderReturn;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderReturnCreateRequest(
        @NotNull UUID orderId,
        UUID orderLineId,
        @NotNull BigDecimal quantity,
        @NotBlank String reason,
        UUID requestedBy,
        @NotNull Instant requestedAt,
        BigDecimal refundAmount,
        @NotNull OrderReturn.Status status) {
}
