package com.smartseason.order.web.dto;

import com.smartseason.order.domain.OrderReturn;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderReturnUpdateRequest(
        UUID orderId,
        UUID orderLineId,
        BigDecimal quantity,
        String reason,
        UUID requestedBy,
        Instant requestedAt,
        BigDecimal refundAmount,
        OrderReturn.Status status) {
}
