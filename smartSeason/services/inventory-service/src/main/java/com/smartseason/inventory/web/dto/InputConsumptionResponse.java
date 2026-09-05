package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.InputConsumption;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InputConsumptionResponse(
        UUID id,
        UUID inputIssueId,
        UUID farmId,
        UUID plotId,
        UUID seasonId,
        String inputCode,
        BigDecimal quantity,
        String unit,
        Instant appliedAt,
        UUID appliedBy,
        BigDecimal areaCoveredHa,
        String evidenceUrl,
        BigDecimal varianceKg,
        Instant createdAt,
        Instant updatedAt) {

    public static InputConsumptionResponse from(InputConsumption entity) {
        return new InputConsumptionResponse(
                entity.getId(),
                entity.getInputIssueId(),
                entity.getFarmId(),
                entity.getPlotId(),
                entity.getSeasonId(),
                entity.getInputCode(),
                entity.getQuantity(),
                entity.getUnit(),
                entity.getAppliedAt(),
                entity.getAppliedBy(),
                entity.getAreaCoveredHa(),
                entity.getEvidenceUrl(),
                entity.getVarianceKg(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
