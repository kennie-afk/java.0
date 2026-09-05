package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.SoilProfile;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SoilProfileResponse(
        UUID id,
        UUID plotId,
        LocalDate sampledAt,
        BigDecimal ph,
        BigDecimal nitrogenPpm,
        BigDecimal phosphorusPpm,
        BigDecimal potassiumPpm,
        BigDecimal organicCarbonPct,
        String texture,
        String labName,
        String reportUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static SoilProfileResponse from(SoilProfile entity) {
        return new SoilProfileResponse(
                entity.getId(),
                entity.getPlotId(),
                entity.getSampledAt(),
                entity.getPh(),
                entity.getNitrogenPpm(),
                entity.getPhosphorusPpm(),
                entity.getPotassiumPpm(),
                entity.getOrganicCarbonPct(),
                entity.getTexture(),
                entity.getLabName(),
                entity.getReportUrl(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
