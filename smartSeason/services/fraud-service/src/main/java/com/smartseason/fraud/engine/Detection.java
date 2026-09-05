package com.smartseason.fraud.engine;

import java.util.Map;

public record Detection(
        Typology typology,
        String ruleCode,
        double confidence,
        Severity severity,
        String explanation,
        Map<String, Object> evidence) {

    public Detection {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be within [0,1] but was " + confidence);
        }
        evidence = Map.copyOf(evidence);
    }

    public static Detection of(Typology typology, String ruleCode, double confidence,
                               String explanation, Map<String, Object> evidence) {
        return new Detection(typology, ruleCode, confidence,
                Severity.fromConfidence(confidence), explanation, evidence);
    }
}
