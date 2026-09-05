package com.smartseason.telemetryingest.web.dto;

import com.smartseason.telemetryingest.domain.TelemetryAnomalyRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryAnomalyRecordUpdateRequest(
        UUID deviceId,
        UUID plotId,
        @Size(max = 255) String metric,
        BigDecimal observedValue,
        BigDecimal expectedMin,
        BigDecimal expectedMax,
        Instant detectedAt,
        TelemetryAnomalyRecord.Severity severity,
        Boolean resolved) {
}
