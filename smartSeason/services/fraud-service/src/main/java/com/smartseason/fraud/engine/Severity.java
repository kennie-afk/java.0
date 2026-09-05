package com.smartseason.fraud.engine;

public enum Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static Severity fromConfidence(double confidence) {
        if (confidence >= 0.90) {
            return CRITICAL;
        }
        if (confidence >= 0.75) {
            return HIGH;
        }
        if (confidence >= 0.50) {
            return MEDIUM;
        }
        return LOW;
    }
}
