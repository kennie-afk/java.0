package com.smartseason.telemetryingest.web.dto;

import com.smartseason.telemetryingest.domain.DownsampledReading;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DownsampledReadingCreateRequest(
        @NotNull UUID deviceId,
        @NotBlank @Size(max = 255) String metric,
        @NotNull Instant bucketStart,
        @NotNull Integer bucketMinutes,
        BigDecimal avgValue,
        BigDecimal minValue,
        BigDecimal maxValue,
        @NotNull Integer sampleCount) {
}
