package com.smartseason.attendance.clockin;

public record ClockInPolicy(
        double minBiometricScore,
        double maxAccuracyM,
        double geofoceToleranceM,
        boolean rejectMockedLocation,
        boolean requireGeofence) {

    public static ClockInPolicy defaults() {
        return new ClockInPolicy(0.80, 100.0, 50.0, true, true);
    }
}
