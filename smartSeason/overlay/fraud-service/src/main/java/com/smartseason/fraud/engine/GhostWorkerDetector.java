package com.smartseason.fraud.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GhostWorkerDetector implements Detector {

    @Override
    public Typology typology() {
        return Typology.GHOST_WORKER;
    }

    @Override
    public String ruleCode() {
        return "GHOST_WORKER_V1";
    }

    @Override
    public List<Detection> evaluate(WorkerActivity activity, RuleSettings settings) {
        if (!activity.contractActive()) {
            return List.of();
        }
        if (activity.shifts().size() >= settings.ghostWorkerMinExpectedShifts()) {
            return List.of();
        }
        if (!activity.clockEvents().isEmpty()) {
            return List.of();
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("windowStart", activity.windowStart().toString());
        evidence.put("windowEnd", activity.windowEnd().toString());
        evidence.put("shiftCount", activity.shifts().size());
        evidence.put("clockEventCount", activity.clockEvents().size());
        evidence.put("pieceRateEntryCount", activity.pieceRates().size());

        double confidence = activity.pieceRates().isEmpty() ? 0.78 : 0.94;
        String explanation = activity.pieceRates().isEmpty()
                ? "an active contract produced no attendance and no work evidence in the window"
                : "piece-rate output was recorded with no attendance at all, which cannot happen "
                        + "if the worker was present";

        return List.of(Detection.of(typology(), ruleCode(), confidence, explanation, evidence));
    }
}
