package com.smartseason.weather.web.dto;

import com.smartseason.weather.domain.WeatherStation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WeatherStationResponse(
        UUID id,
        String externalId,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal elevationM,
        String provider,
        String county,
        Boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static WeatherStationResponse from(WeatherStation entity) {
        return new WeatherStationResponse(
                entity.getId(),
                entity.getExternalId(),
                entity.getName(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getElevationM(),
                entity.getProvider(),
                entity.getCounty(),
                entity.getActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
