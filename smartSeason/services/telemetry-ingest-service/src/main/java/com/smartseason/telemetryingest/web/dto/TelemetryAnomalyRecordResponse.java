package com.smartseason.telemetryingest.web.dto;

import com.smartseason.telemetryingest.domain.TelemetryAnomalyRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryAnomalyRecordResponse(
        UUID id,
        UUID deviceId,
        UUID plotId,
        String metric,
        BigDecimal observedValue,
        BigDecimal expectedMin,
        BigDecimal expectedMax,
        Instant detectedAt,
        TelemetryAnomalyRecord.Severity severity,
        Boolean resolved,
        Instant createdAt,
        Instant updatedAt) {

    public static TelemetryAnomalyRecordResponse from(TelemetryAnomalyRecord entity) {
        return new TelemetryAnomalyRecordResponse(
                entity.getId(),
                entity.getDeviceId(),
                entity.getPlotId(),
                entity.getMetric(),
                entity.getObservedValue(),
                entity.getExpectedMin(),
                entity.getExpectedMax(),
                entity.getDetectedAt(),
                entity.getSeverity(),
                entity.getResolved(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
