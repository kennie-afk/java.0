package com.smartseason.telemetryingest.web.dto;

import com.smartseason.telemetryingest.domain.TelemetryReading;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryReadingUpdateRequest(
        UUID deviceId,
        UUID plotId,
        @Size(max = 255) String metric,
        BigDecimal value,
        @Size(max = 255) String unit,
        Instant recordedAt,
        Instant receivedAt,
        TelemetryReading.Quality quality,
        String raw) {
}
