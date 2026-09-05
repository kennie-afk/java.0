package com.smartseason.payout.web.dto;

import com.smartseason.payout.domain.Settlement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementUpdateRequest(
        @Size(max = 255) String settlementNumber,
        UUID payeeOrgId,
        UUID payeeUserId,
        UUID orderId,
        BigDecimal grossAmount,
        BigDecimal commission,
        BigDecimal fees,
        BigDecimal netAmount,
        @Size(max = 255) String currency,
        LocalDate periodStart,
        LocalDate periodEnd,
        Instant dueAt,
        Settlement.Status status,
        UUID approvedBy) {
}
