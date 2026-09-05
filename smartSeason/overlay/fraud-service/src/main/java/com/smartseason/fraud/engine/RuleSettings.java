package com.smartseason.fraud.engine;

public record RuleSettings(
        double minBiometricScore,
        double maxPlausibleSpeedKph,
        double pieceRateInflationRatio,
        double maxShiftHours,
        int ghostWorkerMinExpectedShifts) {

    public static RuleSettings defaults() {
        return new RuleSettings(0.80, 65.0, 1.75, 14.0, 1);
    }
}
