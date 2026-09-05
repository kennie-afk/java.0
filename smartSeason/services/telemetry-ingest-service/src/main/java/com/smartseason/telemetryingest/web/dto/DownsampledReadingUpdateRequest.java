package com.smartseason.telemetryingest.web.dto;

import com.smartseason.telemetryingest.domain.DownsampledReading;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DownsampledReadingUpdateRequest(
        UUID deviceId,
        @Size(max = 255) String metric,
        Instant bucketStart,
        Integer bucketMinutes,
        BigDecimal avgValue,
        BigDecimal minValue,
        BigDecimal maxValue,
        Integer sampleCount) {
}
