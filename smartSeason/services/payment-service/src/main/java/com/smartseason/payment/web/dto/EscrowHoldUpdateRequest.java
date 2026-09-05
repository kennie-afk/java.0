package com.smartseason.payment.web.dto;

import com.smartseason.payment.domain.EscrowHold;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EscrowHoldUpdateRequest(
        UUID paymentIntentId,
        UUID orderId,
        BigDecimal amount,
        @Size(max = 255) String currency,
        Instant heldAt,
        Instant releaseDueAt,
        Instant releasedAt,
        UUID releasedTo,
        Instant refundedAt,
        EscrowHold.Status status,
        @Size(max = 255) String releaseCondition) {
}
