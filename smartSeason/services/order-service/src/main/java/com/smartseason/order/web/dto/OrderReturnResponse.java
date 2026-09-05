package com.smartseason.order.web.dto;

import com.smartseason.order.domain.OrderReturn;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderReturnResponse(
        UUID id,
        UUID orderId,
        UUID orderLineId,
        BigDecimal quantity,
        String reason,
        UUID requestedBy,
        Instant requestedAt,
        BigDecimal refundAmount,
        OrderReturn.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderReturnResponse from(OrderReturn entity) {
        return new OrderReturnResponse(
                entity.getId(),
                entity.getOrderId(),
                entity.getOrderLineId(),
                entity.getQuantity(),
                entity.getReason(),
                entity.getRequestedBy(),
                entity.getRequestedAt(),
                entity.getRefundAmount(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
