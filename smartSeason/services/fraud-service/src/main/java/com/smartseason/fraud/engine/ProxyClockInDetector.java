package com.smartseason.fraud.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProxyClockInDetector implements Detector {

    @Override
    public Typology typology() {
        return Typology.PROXY_CLOCK_IN;
    }

    @Override
    public String ruleCode() {
        return "PROXY_CLOCK_IN_V1";
    }

    @Override
    public List<Detection> evaluate(WorkerActivity activity, RuleSettings settings) {
        List<Detection> detections = new ArrayList<>();

        for (WorkerActivity.ClockPoint point : activity.clockEvents()) {
            List<String> reasons = new ArrayList<>();
            double confidence = 0.0;

            if (point.mockLocation()) {
                reasons.add("device reported a mocked location");
                confidence += 0.55;
            }
            if (point.biometricScore() != null && point.biometricScore() < settings.minBiometricScore()) {
                reasons.add("biometric match %.2f below the %.2f threshold"
                        .formatted(point.biometricScore(), settings.minBiometricScore()));
                confidence += 0.35;
            }
            if (!point.insideGeofence()) {
                reasons.add("clock event fell outside the farm geofence");
                confidence += 0.25;
            }

            if (reasons.isEmpty()) {
                continue;
            }

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("occurredAt", point.occurredAt().toString());
            evidence.put("biometricScore", point.biometricScore());
            evidence.put("insideGeofence", point.insideGeofence());
            evidence.put("mockLocation", point.mockLocation());
            evidence.put("deviceId", point.deviceId());

            detections.add(Detection.of(typology(), ruleCode(), Math.min(1.0, confidence),
                    String.join("; ", reasons), evidence));
        }

        return detections;
    }
}
