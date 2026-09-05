package com.smartseason.payout.web.dto;

import com.smartseason.payout.domain.Settlement;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementResponse(
        UUID id,
        String settlementNumber,
        UUID payeeOrgId,
        UUID payeeUserId,
        UUID orderId,
        BigDecimal grossAmount,
        BigDecimal commission,
        BigDecimal fees,
        BigDecimal netAmount,
        String currency,
        LocalDate periodStart,
        LocalDate periodEnd,
        Instant dueAt,
        Settlement.Status status,
        UUID approvedBy,
        Instant createdAt,
        Instant updatedAt) {

    public static SettlementResponse from(Settlement entity) {
        return new SettlementResponse(
                entity.getId(),
                entity.getSettlementNumber(),
                entity.getPayeeOrgId(),
                entity.getPayeeUserId(),
                entity.getOrderId(),
                entity.getGrossAmount(),
                entity.getCommission(),
                entity.getFees(),
                entity.getNetAmount(),
                entity.getCurrency(),
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getDueAt(),
                entity.getStatus(),
                entity.getApprovedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
