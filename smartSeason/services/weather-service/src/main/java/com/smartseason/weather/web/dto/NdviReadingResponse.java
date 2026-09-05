package com.smartseason.weather.web.dto;

import com.smartseason.weather.domain.NdviReading;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record NdviReadingResponse(
        UUID id,
        UUID plotId,
        String geoCell,
        LocalDate capturedOn,
        BigDecimal ndvi,
        BigDecimal cloudCoverPct,
        String satellite,
        String tileUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static NdviReadingResponse from(NdviReading entity) {
        return new NdviReadingResponse(
                entity.getId(),
                entity.getPlotId(),
                entity.getGeoCell(),
                entity.getCapturedOn(),
                entity.getNdvi(),
                entity.getCloudCoverPct(),
                entity.getSatellite(),
                entity.getTileUrl(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
