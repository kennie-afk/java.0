package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.Plot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlotResponse(
        UUID id,
        UUID farmId,
        String name,
        BigDecimal areaHa,
        String boundaryGeojson,
        BigDecimal centroidLat,
        BigDecimal centroidLng,
        Boolean irrigated,
        String currentCrop,
        Plot.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static PlotResponse from(Plot entity) {
        return new PlotResponse(
                entity.getId(),
                entity.getFarmId(),
                entity.getName(),
                entity.getAreaHa(),
                entity.getBoundaryGeojson(),
                entity.getCentroidLat(),
                entity.getCentroidLng(),
                entity.getIrrigated(),
                entity.getCurrentCrop(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
