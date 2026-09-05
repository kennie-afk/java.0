package com.smartseason.attendance.clockin;

import com.smartseason.attendance.domain.Geofence;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GeofenceEvaluator {

    private final ClockInPolicy policy;

    public GeofenceEvaluator(ClockInPolicy policy) {
        this.policy = policy;
    }

    public ClockInDecision evaluate(ClockInAttempt attempt, List<Geofence> geofences) {
        List<String> reasons = new ArrayList<>();

        if (policy.rejectMockedLocation() && attempt.mockLocation()) {
            reasons.add("device reported a mocked location");
            return new ClockInDecision(ClockInDecision.Verdict.REJECTED, false, null, null, reasons);
        }

        if (attempt.latitude() == null || attempt.longitude() == null) {
            reasons.add("no position supplied");
            return new ClockInDecision(
                    policy.requireGeofence()
                            ? ClockInDecision.Verdict.REJECTED
                            : ClockInDecision.Verdict.FLAGGED,
                    false, null, null, reasons);
        }

        if (attempt.accuracyM() != null && attempt.accuracyM() > policy.maxAccuracyM()) {
            reasons.add("position accuracy %.0fm exceeds the %.0fm limit"
                    .formatted(attempt.accuracyM(), policy.maxAccuracyM()));
        }

        UUID matched = null;
        double best = Double.MAX_VALUE;

        for (Geofence fence : geofences) {
            if (!Boolean.TRUE.equals(fence.getActive())
                    || fence.getCenterLat() == null || fence.getCenterLng() == null) {
                continue;
            }
            double distance = Geo.distanceMetres(
                    attempt.latitude(), attempt.longitude(),
                    fence.getCenterLat().doubleValue(), fence.getCenterLng().doubleValue());

            double allowed = radius(fence) + policy.geofoceToleranceM();
            if (distance <= allowed && distance < best) {
                best = distance;
                matched = fence.getId();
            }
        }

        boolean inside = matched != null;
        if (!inside) {
            reasons.add("position falls outside every active geofence for the farm");
        }

        if (attempt.biometricScore() != null
                && attempt.biometricScore() < policy.minBiometricScore()) {
            reasons.add("biometric match %.2f below the %.2f threshold"
                    .formatted(attempt.biometricScore(), policy.minBiometricScore()));
        }

        ClockInDecision.Verdict verdict;
        if (!inside && policy.requireGeofence()) {
            verdict = ClockInDecision.Verdict.REJECTED;
        } else if (!reasons.isEmpty()) {
            verdict = ClockInDecision.Verdict.FLAGGED;
        } else {
            verdict = ClockInDecision.Verdict.ACCEPTED;
        }

        return new ClockInDecision(verdict, inside, matched,
                inside ? best : null, reasons);
    }

    private static double radius(Geofence fence) {
        Integer radius = fence.getRadiusM();
        return radius == null ? 0.0 : radius.doubleValue();
    }

    public static BigDecimal toDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
