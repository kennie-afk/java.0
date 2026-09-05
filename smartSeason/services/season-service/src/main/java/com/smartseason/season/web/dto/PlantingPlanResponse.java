package com.smartseason.season.web.dto;

import com.smartseason.season.domain.PlantingPlan;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlantingPlanResponse(
        UUID id,
        UUID seasonId,
        BigDecimal seedRateKgHa,
        String spacingCm,
        Integer targetPopulation,
        String fertiliserPlan,
        String irrigationPlan,
        UUID approvedBy,
        Instant approvedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static PlantingPlanResponse from(PlantingPlan entity) {
        return new PlantingPlanResponse(
                entity.getId(),
                entity.getSeasonId(),
                entity.getSeedRateKgHa(),
                entity.getSpacingCm(),
                entity.getTargetPopulation(),
                entity.getFertiliserPlan(),
                entity.getIrrigationPlan(),
                entity.getApprovedBy(),
                entity.getApprovedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
