package com.smartseason.fraud.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FraudRuleEngine {

    private final List<Detector> detectors;
    private final RuleSettings settings;

    public FraudRuleEngine(List<Detector> detectors, RuleSettings settings) {
        this.detectors = List.copyOf(detectors);
        this.settings = settings;
    }

    public static FraudRuleEngine withDefaults() {
        return new FraudRuleEngine(
                List.of(new GhostWorkerDetector(),
                        new ProxyClockInDetector(),
                        new ImpossibleTravelDetector(),
                        new PieceRateInflationDetector(),
                        new HoursInflationDetector()),
                RuleSettings.defaults());
    }

    public List<Detection> evaluate(WorkerActivity activity) {
        List<Detection> detections = new ArrayList<>();
        for (Detector detector : detectors) {
            detections.addAll(detector.evaluate(activity, settings));
        }
        detections.sort(Comparator.comparingDouble(Detection::confidence).reversed());
        return List.copyOf(detections);
    }

    public int riskScore(List<Detection> detections) {
        if (detections.isEmpty()) {
            return 0;
        }
        double residual = 1.0;
        for (Detection detection : detections) {
            residual *= (1.0 - detection.confidence());
        }
        return (int) Math.round((1.0 - residual) * 100);
    }

    public RuleSettings settings() {
        return settings;
    }
}
