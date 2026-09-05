package com.smartseason.payout.web.dto;

import com.smartseason.payout.domain.PayoutHold;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutHoldUpdateRequest(
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
        String notes) {
}
