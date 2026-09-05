package com.smartseason.telemetryingest.web.dto;

import com.smartseason.telemetryingest.domain.TelemetryReading;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryReadingResponse(
        UUID id,
        UUID deviceId,
        UUID plotId,
        String metric,
        BigDecimal value,
        String unit,
        Instant recordedAt,
        Instant receivedAt,
        TelemetryReading.Quality quality,
        String raw,
        Instant createdAt,
        Instant updatedAt) {

    public static TelemetryReadingResponse from(TelemetryReading entity) {
        return new TelemetryReadingResponse(
                entity.getId(),
                entity.getDeviceId(),
                entity.getPlotId(),
                entity.getMetric(),
                entity.getValue(),
                entity.getUnit(),
                entity.getRecordedAt(),
                entity.getReceivedAt(),
                entity.getQuality(),
                entity.getRaw(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
