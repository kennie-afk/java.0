package com.smartseason.payment.web.dto;

import com.smartseason.payment.domain.EscrowHold;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EscrowHoldResponse(
        UUID id,
        UUID paymentIntentId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        Instant heldAt,
        Instant releaseDueAt,
        Instant releasedAt,
        UUID releasedTo,
        Instant refundedAt,
        EscrowHold.Status status,
        String releaseCondition,
        Instant createdAt,
        Instant updatedAt) {

    public static EscrowHoldResponse from(EscrowHold entity) {
        return new EscrowHoldResponse(
                entity.getId(),
                entity.getPaymentIntentId(),
                entity.getOrderId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getHeldAt(),
                entity.getReleaseDueAt(),
                entity.getReleasedAt(),
                entity.getReleasedTo(),
                entity.getRefundedAt(),
                entity.getStatus(),
                entity.getReleaseCondition(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
