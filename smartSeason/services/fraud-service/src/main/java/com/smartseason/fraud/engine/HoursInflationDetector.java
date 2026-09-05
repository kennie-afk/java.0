package com.smartseason.fraud.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HoursInflationDetector implements Detector {

    @Override
    public Typology typology() {
        return Typology.HOURS_INFLATION;
    }

    @Override
    public String ruleCode() {
        return "HOURS_INFLATION_V1";
    }

    @Override
    public List<Detection> evaluate(WorkerActivity activity, RuleSettings settings) {
        List<Detection> detections = new ArrayList<>();

        for (WorkerActivity.Shift shift : activity.shifts()) {
            if (shift.endedAt() == null) {
                continue;
            }
            double hours = Duration.between(shift.startedAt(), shift.endedAt()).toMinutes() / 60.0;
            if (hours <= settings.maxShiftHours()) {
                continue;
            }

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("startedAt", shift.startedAt().toString());
            evidence.put("endedAt", shift.endedAt().toString());
            evidence.put("hours", Math.round(hours * 100.0) / 100.0);
            evidence.put("threshold", settings.maxShiftHours());

            double confidence = Math.min(1.0, 0.5 + (hours - settings.maxShiftHours()) * 0.06);

            detections.add(Detection.of(typology(), ruleCode(), confidence,
                    "a shift of %.1f h exceeds the %.1f h plausibility ceiling"
                            .formatted(hours, settings.maxShiftHours()),
                    evidence));
        }

        return detections;
    }
}
