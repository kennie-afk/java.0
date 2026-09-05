package com.smartseason.attendance.web.dto;

import com.smartseason.attendance.domain.ClockEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClockEventCreateRequest(
        @NotNull UUID workerId,
        @NotNull UUID farmId,
        UUID shiftId,
        @NotNull ClockEvent.EventType eventType,
        @NotNull Instant occurredAt,
        @NotNull Instant recordedAt,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracyM,
        UUID geofenceId,
        @NotNull Boolean insideGeofence,
        BigDecimal biometricScore,
        @Size(max = 255) String deviceId,
        @NotNull Boolean mockLocation,
        @NotNull Boolean offlineSynced,
        @Size(max = 255) String clientEventId,
        @NotNull ClockEvent.Verdict verdict,
        @Size(max = 255) String flagReason) {
}
