package com.smartseason.attendance.web.dto;

import com.smartseason.attendance.domain.ClockEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClockEventUpdateRequest(
        UUID workerId,
        UUID farmId,
        UUID shiftId,
        ClockEvent.EventType eventType,
        Instant occurredAt,
        Instant recordedAt,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracyM,
        UUID geofenceId,
        Boolean insideGeofence,
        BigDecimal biometricScore,
        @Size(max = 255) String deviceId,
        Boolean mockLocation,
        Boolean offlineSynced,
        @Size(max = 255) String clientEventId,
        ClockEvent.Verdict verdict,
        @Size(max = 255) String flagReason) {
}
