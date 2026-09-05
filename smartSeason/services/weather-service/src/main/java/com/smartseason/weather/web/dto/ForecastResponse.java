package com.smartseason.weather.web.dto;

import com.smartseason.weather.domain.Forecast;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ForecastResponse(
        UUID id,
        String geoCell,
        LocalDate forecastFor,
        Instant issuedAt,
        BigDecimal tempMinC,
        BigDecimal tempMaxC,
        BigDecimal rainfallMm,
        BigDecimal humidityPct,
        BigDecimal windKph,
        String conditions,
        String provider,
        Instant createdAt,
        Instant updatedAt) {

    public static ForecastResponse from(Forecast entity) {
        return new ForecastResponse(
                entity.getId(),
                entity.getGeoCell(),
                entity.getForecastFor(),
                entity.getIssuedAt(),
                entity.getTempMinC(),
                entity.getTempMaxC(),
                entity.getRainfallMm(),
                entity.getHumidityPct(),
                entity.getWindKph(),
                entity.getConditions(),
                entity.getProvider(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
