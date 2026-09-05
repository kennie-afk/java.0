package com.smartseason.logistics.web.dto;

import com.smartseason.logistics.domain.ColdChainReading;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ColdChainReadingResponse(
        UUID id,
        UUID transportJobId,
        Instant recordedAt,
        BigDecimal temperatureC,
        BigDecimal humidityPct,
        String deviceId,
        Boolean breach,
        Instant createdAt,
        Instant updatedAt) {

    public static ColdChainReadingResponse from(ColdChainReading entity) {
        return new ColdChainReadingResponse(
                entity.getId(),
                entity.getTransportJobId(),
                entity.getRecordedAt(),
                entity.getTemperatureC(),
                entity.getHumidityPct(),
                entity.getDeviceId(),
                entity.getBreach(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
