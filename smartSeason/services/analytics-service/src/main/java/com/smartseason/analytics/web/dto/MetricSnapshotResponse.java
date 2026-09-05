package com.smartseason.analytics.web.dto;

import com.smartseason.analytics.domain.MetricSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MetricSnapshotResponse(
        UUID id,
        String metricKey,
        String dimension,
        String dimensionValue,
        Instant periodStart,
        Instant periodEnd,
        MetricSnapshot.Granularity granularity,
        BigDecimal value,
        String unit,
        Instant computedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static MetricSnapshotResponse from(MetricSnapshot entity) {
        return new MetricSnapshotResponse(
                entity.getId(),
                entity.getMetricKey(),
                entity.getDimension(),
                entity.getDimensionValue(),
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getGranularity(),
                entity.getValue(),
                entity.getUnit(),
                entity.getComputedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
