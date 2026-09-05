package com.smartseason.telemetryingest.web.dto;

import com.smartseason.telemetryingest.domain.DownsampledReading;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DownsampledReadingResponse(
        UUID id,
        UUID deviceId,
        String metric,
        Instant bucketStart,
        Integer bucketMinutes,
        BigDecimal avgValue,
        BigDecimal minValue,
        BigDecimal maxValue,
        Integer sampleCount,
        Instant createdAt,
        Instant updatedAt) {

    public static DownsampledReadingResponse from(DownsampledReading entity) {
        return new DownsampledReadingResponse(
                entity.getId(),
                entity.getDeviceId(),
                entity.getMetric(),
                entity.getBucketStart(),
                entity.getBucketMinutes(),
                entity.getAvgValue(),
                entity.getMinValue(),
                entity.getMaxValue(),
                entity.getSampleCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
