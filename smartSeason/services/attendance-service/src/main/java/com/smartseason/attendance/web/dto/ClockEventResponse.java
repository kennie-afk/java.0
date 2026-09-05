package com.smartseason.attendance.web.dto;

import com.smartseason.attendance.domain.ClockEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClockEventResponse(
        UUID id,
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
        String deviceId,
        Boolean mockLocation,
        Boolean offlineSynced,
        String clientEventId,
        ClockEvent.Verdict verdict,
        String flagReason,
        Instant createdAt,
        Instant updatedAt) {

    public static ClockEventResponse from(ClockEvent entity) {
        return new ClockEventResponse(
                entity.getId(),
                entity.getWorkerId(),
                entity.getFarmId(),
                entity.getShiftId(),
                entity.getEventType(),
                entity.getOccurredAt(),
                entity.getRecordedAt(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getAccuracyM(),
                entity.getGeofenceId(),
                entity.getInsideGeofence(),
                entity.getBiometricScore(),
                entity.getDeviceId(),
                entity.getMockLocation(),
                entity.getOfflineSynced(),
                entity.getClientEventId(),
                entity.getVerdict(),
                entity.getFlagReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
