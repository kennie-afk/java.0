package com.smartseason.fraud.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkerActivity(
        UUID workerId,
        UUID farmId,
        boolean contractActive,
        Instant windowStart,
        Instant windowEnd,
        List<ClockPoint> clockEvents,
        List<Shift> shifts,
        List<PieceRate> pieceRates,
        BigDecimal peerMedianQuantity,
        BigDecimal plotAreaHa,
        BigDecimal agronomicMaxPerHa) {

    public record ClockPoint(
            Instant occurredAt,
            Double latitude,
            Double longitude,
            Double accuracyM,
            Double biometricScore,
            boolean insideGeofence,
            boolean mockLocation,
            String deviceId) {
    }

    public record Shift(Instant startedAt, Instant endedAt, int breakMinutes) {
    }

    public record PieceRate(Instant recordedAt, String taskCode, BigDecimal quantity, String unit) {
    }
}
