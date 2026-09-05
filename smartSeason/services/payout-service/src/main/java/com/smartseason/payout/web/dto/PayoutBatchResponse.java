package com.smartseason.payout.web.dto;

import com.smartseason.payout.domain.PayoutBatch;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutBatchResponse(
        UUID id,
        String batchNumber,
        UUID farmId,
        PayoutBatch.PayoutType payoutType,
        Integer itemCount,
        BigDecimal totalAmount,
        String currency,
        Instant scheduledFor,
        Instant submittedAt,
        Instant completedAt,
        UUID createdBy,
        PayoutBatch.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static PayoutBatchResponse from(PayoutBatch entity) {
        return new PayoutBatchResponse(
                entity.getId(),
                entity.getBatchNumber(),
                entity.getFarmId(),
                entity.getPayoutType(),
                entity.getItemCount(),
                entity.getTotalAmount(),
                entity.getCurrency(),
                entity.getScheduledFor(),
                entity.getSubmittedAt(),
                entity.getCompletedAt(),
                entity.getCreatedBy(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
