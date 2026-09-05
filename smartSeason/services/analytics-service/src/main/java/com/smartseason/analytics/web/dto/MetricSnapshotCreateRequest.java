package com.smartseason.analytics.web.dto;

import com.smartseason.analytics.domain.MetricSnapshot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record MetricSnapshotCreateRequest(
        @NotBlank @Size(max = 255) String metricKey,
        @Size(max = 255) String dimension,
        @Size(max = 255) String dimensionValue,
        @NotNull Instant periodStart,
        @NotNull Instant periodEnd,
        @NotNull MetricSnapshot.Granularity granularity,
        @NotNull BigDecimal value,
        @Size(max = 255) String unit,
        @NotNull Instant computedAt) {
}
