package com.smartseason.fraud.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImpossibleTravelDetector implements Detector {

    private static final double MIN_SEPARATION_KM = 0.5;

    @Override
    public Typology typology() {
        return Typology.PROXY_CLOCK_IN;
    }

    @Override
    public String ruleCode() {
        return "IMPOSSIBLE_TRAVEL_V1";
    }

    @Override
    public List<Detection> evaluate(WorkerActivity activity, RuleSettings settings) {
        List<WorkerActivity.ClockPoint> located = activity.clockEvents().stream()
                .filter(point -> point.latitude() != null && point.longitude() != null)
                .sorted(Comparator.comparing(WorkerActivity.ClockPoint::occurredAt))
                .toList();

        List<Detection> detections = new ArrayList<>();

        for (int i = 1; i < located.size(); i++) {
            WorkerActivity.ClockPoint previous = located.get(i - 1);
            WorkerActivity.ClockPoint current = located.get(i);

            double km = Geo.distanceKm(previous.latitude(), previous.longitude(),
                    current.latitude(), current.longitude());
            if (km < MIN_SEPARATION_KM) {
                continue;
            }

            double hours = Duration.between(previous.occurredAt(), current.occurredAt()).toMillis()
                    / 3_600_000.0;
            if (hours <= 0) {
                continue;
            }

            double kph = km / hours;
            if (kph <= settings.maxPlausibleSpeedKph()) {
                continue;
            }

            double excess = kph / settings.maxPlausibleSpeedKph();
            double confidence = Math.min(1.0, 0.55 + 0.15 * (excess - 1.0));

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("fromAt", previous.occurredAt().toString());
            evidence.put("toAt", current.occurredAt().toString());
            evidence.put("distanceKm", round(km));
            evidence.put("elapsedHours", round(hours));
            evidence.put("impliedSpeedKph", round(kph));
            evidence.put("thresholdKph", settings.maxPlausibleSpeedKph());

            detections.add(Detection.of(typology(), ruleCode(), confidence,
                    "two clock events %.1f km apart within %.2f h imply %.0f km/h"
                            .formatted(km, hours, kph),
                    evidence));
        }

        return detections;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
