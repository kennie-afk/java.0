package com.smartseason.fraud.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PieceRateInflationDetector implements Detector {

    @Override
    public Typology typology() {
        return Typology.PIECE_RATE_INFLATION;
    }

    @Override
    public String ruleCode() {
        return "PIECE_RATE_INFLATION_V1";
    }

    @Override
    public List<Detection> evaluate(WorkerActivity activity, RuleSettings settings) {
        List<Detection> detections = new ArrayList<>();

        BigDecimal recorded = activity.pieceRates().stream()
                .map(WorkerActivity.PieceRate::quantity)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (recorded.signum() == 0) {
            return detections;
        }

        BigDecimal peerMedian = activity.peerMedianQuantity();
        if (peerMedian != null && peerMedian.signum() > 0) {
            BigDecimal ratio = recorded.divide(peerMedian, 4, RoundingMode.HALF_UP);
            if (ratio.doubleValue() > settings.pieceRateInflationRatio()) {
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("recordedQuantity", recorded);
                evidence.put("peerMedian", peerMedian);
                evidence.put("ratio", ratio);
                evidence.put("threshold", settings.pieceRateInflationRatio());

                double confidence = Math.min(1.0,
                        0.45 + 0.2 * (ratio.doubleValue() - settings.pieceRateInflationRatio()));

                detections.add(Detection.of(typology(), ruleCode(), confidence,
                        "recorded %s against a peer median of %s (%.2fx)"
                                .formatted(recorded.toPlainString(), peerMedian.toPlainString(),
                                        ratio.doubleValue()),
                        evidence));
            }
        }

        BigDecimal area = activity.plotAreaHa();
        BigDecimal maxPerHa = activity.agronomicMaxPerHa();
        if (area != null && area.signum() > 0 && maxPerHa != null && maxPerHa.signum() > 0) {
            BigDecimal ceiling = area.multiply(maxPerHa);
            if (recorded.compareTo(ceiling) > 0) {
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("recordedQuantity", recorded);
                evidence.put("plotAreaHa", area);
                evidence.put("agronomicMaxPerHa", maxPerHa);
                evidence.put("agronomicCeiling", ceiling);

                detections.add(Detection.of(typology(), ruleCode(), 0.92,
                        "recorded %s exceeds the agronomic ceiling of %s for %s ha"
                                .formatted(recorded.toPlainString(), ceiling.toPlainString(),
                                        area.toPlainString()),
                        evidence));
            }
        }

        return detections;
    }
}
