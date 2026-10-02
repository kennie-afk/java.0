package com.smartseason.fraud.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
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
        // Map.copyOf rejects null values, and evidence routinely has them: a clock event with
        // no biometric score or device id is the ordinary case, not an error. Absent fields are
        // left out rather than letting one of them abort the whole evaluation.
        Map<String, Object> kept = new LinkedHashMap<>();
        evidence.forEach((key, value) -> {
            if (value != null) {
                kept.put(key, value);
            }
        });
        evidence = Collections.unmodifiableMap(kept);
    }

    public static Detection of(Typology typology, String ruleCode, double confidence,
                               String explanation, Map<String, Object> evidence) {
        return new Detection(typology, ruleCode, confidence,
                Severity.fromConfidence(confidence), explanation, evidence);
    }
}
