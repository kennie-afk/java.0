package com.smartseason.analytics.web.dto;

import com.smartseason.analytics.domain.MetricSnapshot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record MetricSnapshotUpdateRequest(
        @Size(max = 255) String metricKey,
        @Size(max = 255) String dimension,
        @Size(max = 255) String dimensionValue,
        Instant periodStart,
        Instant periodEnd,
        MetricSnapshot.Granularity granularity,
        BigDecimal value,
        @Size(max = 255) String unit,
        Instant computedAt) {
}
