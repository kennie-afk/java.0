package com.smartseason.telemetryingest.web.dto;

import com.smartseason.telemetryingest.domain.TelemetryReading;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryReadingCreateRequest(
        @NotNull UUID deviceId,
        UUID plotId,
        @NotBlank @Size(max = 255) String metric,
        @NotNull BigDecimal value,
        @Size(max = 255) String unit,
        @NotNull Instant recordedAt,
        @NotNull Instant receivedAt,
        @NotNull TelemetryReading.Quality quality,
        String raw) {
}
