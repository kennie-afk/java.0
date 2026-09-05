package com.smartseason.season.web.dto;

import com.smartseason.season.domain.Season;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SeasonResponse(
        UUID id,
        UUID plotId,
        UUID farmId,
        String cropCode,
        String variety,
        LocalDate startDate,
        LocalDate expectedHarvestDate,
        LocalDate actualHarvestDate,
        BigDecimal expectedYieldKg,
        BigDecimal actualYieldKg,
        String currentStage,
        Season.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static SeasonResponse from(Season entity) {
        return new SeasonResponse(
                entity.getId(),
                entity.getPlotId(),
                entity.getFarmId(),
                entity.getCropCode(),
                entity.getVariety(),
                entity.getStartDate(),
                entity.getExpectedHarvestDate(),
                entity.getActualHarvestDate(),
                entity.getExpectedYieldKg(),
                entity.getActualYieldKg(),
                entity.getCurrentStage(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
