package com.smartseason.payout.web.dto;

import com.smartseason.payout.domain.PayoutBatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutBatchCreateRequest(
        @NotBlank @Size(max = 255) String batchNumber,
        UUID farmId,
        @NotNull PayoutBatch.PayoutType payoutType,
        @NotNull Integer itemCount,
        @NotNull BigDecimal totalAmount,
        @NotBlank @Size(max = 255) String currency,
        Instant scheduledFor,
        Instant submittedAt,
        Instant completedAt,
        UUID createdBy,
        @NotNull PayoutBatch.Status status) {
}
