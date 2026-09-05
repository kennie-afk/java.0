package com.smartseason.payout.web.dto;

import com.smartseason.payout.domain.PayoutItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutItemUpdateRequest(
        UUID batchId,
        UUID settlementId,
        PayoutItem.PayeeType payeeType,
        UUID payeeId,
        @Size(max = 255) String payeeName,
        @Size(max = 255) String payeePhone,
        BigDecimal amount,
        @Size(max = 255) String currency,
        UUID paymentIntentId,
        PayoutItem.Status status,
        @Size(max = 255) String failureReason,
        Instant sentAt,
        Instant paidAt,
        @Size(max = 255) String idempotencyKey) {
}
