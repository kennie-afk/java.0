package com.smartseason.payout.web.dto;

import com.smartseason.payout.domain.PayoutHold;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutHoldResponse(
        UUID id,
        UUID payoutItemId,
        UUID payeeId,
        PayoutHold.Reason reason,
        UUID fraudCaseId,
        BigDecimal amount,
        Instant heldAt,
        UUID heldBy,
        Instant releasedAt,
        UUID releasedBy,
        PayoutHold.Status status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static PayoutHoldResponse from(PayoutHold entity) {
        return new PayoutHoldResponse(
                entity.getId(),
                entity.getPayoutItemId(),
                entity.getPayeeId(),
                entity.getReason(),
                entity.getFraudCaseId(),
                entity.getAmount(),
                entity.getHeldAt(),
                entity.getHeldBy(),
                entity.getReleasedAt(),
                entity.getReleasedBy(),
                entity.getStatus(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
