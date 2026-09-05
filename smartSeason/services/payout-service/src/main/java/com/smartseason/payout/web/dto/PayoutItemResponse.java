package com.smartseason.payout.web.dto;

import com.smartseason.payout.domain.PayoutItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutItemResponse(
        UUID id,
        UUID batchId,
        UUID settlementId,
        PayoutItem.PayeeType payeeType,
        UUID payeeId,
        String payeeName,
        String payeePhone,
        BigDecimal amount,
        String currency,
        UUID paymentIntentId,
        PayoutItem.Status status,
        String failureReason,
        Instant sentAt,
        Instant paidAt,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt) {

    public static PayoutItemResponse from(PayoutItem entity) {
        return new PayoutItemResponse(
                entity.getId(),
                entity.getBatchId(),
                entity.getSettlementId(),
                entity.getPayeeType(),
                entity.getPayeeId(),
                entity.getPayeeName(),
                entity.getPayeePhone(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getPaymentIntentId(),
                entity.getStatus(),
                entity.getFailureReason(),
                entity.getSentAt(),
                entity.getPaidAt(),
                entity.getIdempotencyKey(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
