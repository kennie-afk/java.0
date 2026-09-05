package com.smartseason.attendance.clockin;

import java.time.Instant;
import java.util.UUID;

public record ClockInAttempt(
        UUID workerId,
        UUID farmId,
        Instant occurredAt,
        Double latitude,
        Double longitude,
        Double accuracyM,
        Double biometricScore,
        boolean mockLocation,
        String deviceId) {
}
