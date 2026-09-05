package com.smartseason.telemetryingest.web.dto;

import com.smartseason.telemetryingest.domain.TelemetryAnomalyRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryAnomalyRecordCreateRequest(
        @NotNull UUID deviceId,
        UUID plotId,
        @NotBlank @Size(max = 255) String metric,
        @NotNull BigDecimal observedValue,
        BigDecimal expectedMin,
        BigDecimal expectedMax,
        @NotNull Instant detectedAt,
        @NotNull TelemetryAnomalyRecord.Severity severity,
        @NotNull Boolean resolved) {
}
