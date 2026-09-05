package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.InputIssue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InputIssueResponse(
        UUID id,
        UUID farmId,
        UUID plotId,
        UUID seasonId,
        String inputCode,
        String inputName,
        BigDecimal quantity,
        String unit,
        UUID issuedTo,
        UUID issuedBy,
        Instant issuedAt,
        BigDecimal unitCost,
        BigDecimal expectedRatePerHa,
        InputIssue.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static InputIssueResponse from(InputIssue entity) {
        return new InputIssueResponse(
                entity.getId(),
                entity.getFarmId(),
                entity.getPlotId(),
                entity.getSeasonId(),
                entity.getInputCode(),
                entity.getInputName(),
                entity.getQuantity(),
                entity.getUnit(),
                entity.getIssuedTo(),
                entity.getIssuedBy(),
                entity.getIssuedAt(),
                entity.getUnitCost(),
                entity.getExpectedRatePerHa(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
