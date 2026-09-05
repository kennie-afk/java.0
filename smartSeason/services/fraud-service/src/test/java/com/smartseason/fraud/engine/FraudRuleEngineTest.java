package com.smartseason.fraud.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FraudRuleEngineTest {

    private static final Instant T0 = Instant.parse("2026-03-02T06:00:00Z");
    private static final UUID WORKER = UUID.randomUUID();
    private static final UUID FARM = UUID.randomUUID();

    private final FraudRuleEngine engine = FraudRuleEngine.withDefaults();

    private WorkerActivity.ClockPoint clock(Instant at, Double lat, Double lon,
                                            Double biometric, boolean inside, boolean mock) {
        return new WorkerActivity.ClockPoint(at, lat, lon, 8.0, biometric, inside, mock, "device-1");
    }

    private WorkerActivity activity(List<WorkerActivity.ClockPoint> clocks,
                                    List<WorkerActivity.Shift> shifts,
                                    List<WorkerActivity.PieceRate> rates,
                                    BigDecimal peerMedian,
                                    BigDecimal areaHa,
                                    BigDecimal maxPerHa,
                                    boolean contractActive) {
        return new WorkerActivity(WORKER, FARM, contractActive, T0, T0.plus(Duration.ofDays(1)),
                clocks, shifts, rates, peerMedian, areaHa, maxPerHa);
    }

    private WorkerActivity cleanActivity() {
        return activity(
                List.of(clock(T0, -1.2921, 36.8219, 0.97, true, false),
                        clock(T0.plus(Duration.ofHours(8)), -1.2921, 36.8219, 0.96, true, false)),
                List.of(new WorkerActivity.Shift(T0, T0.plus(Duration.ofHours(8)), 30)),
                List.of(new WorkerActivity.PieceRate(T0.plus(Duration.ofHours(7)), "HARVEST",
                        new BigDecimal("120"), "kg")),
                new BigDecimal("115"), new BigDecimal("2.0"), new BigDecimal("900"), true);
    }

    @Test
    @DisplayName("an ordinary day of work raises nothing")
    void cleanActivityIsSilent() {
        List<Detection> detections = engine.evaluate(cleanActivity());

        assertThat(detections).isEmpty();
        assertThat(engine.riskScore(detections)).isZero();
    }

    @Nested
    class GhostWorkers {

        @Test
        @DisplayName("an active contract with no attendance and no output is flagged")
        void silentContractIsFlagged() {
            WorkerActivity activity = activity(List.of(), List.of(), List.of(),
                    null, null, null, true);

            List<Detection> detections = engine.evaluate(activity);

            assertThat(detections).hasSize(1);
            assertThat(detections.getFirst().typology()).isEqualTo(Typology.GHOST_WORKER);
            assertThat(detections.getFirst().confidence()).isEqualTo(0.78);
        }

        @Test
        @DisplayName("output recorded with zero attendance scores higher than mere silence")
        void outputWithoutAttendanceScoresHigher() {
            WorkerActivity activity = activity(List.of(), List.of(),
                    List.of(new WorkerActivity.PieceRate(T0, "HARVEST", new BigDecimal("80"), "kg")),
                    null, null, null, true);

            Detection detection = engine.evaluate(activity).stream()
                    .filter(d -> d.typology() == Typology.GHOST_WORKER)
                    .findFirst()
                    .orElseThrow();

            assertThat(detection.confidence()).isEqualTo(0.94);
            assertThat(detection.severity()).isEqualTo(Severity.CRITICAL);
        }

        @Test
        @DisplayName("an inactive contract is not a ghost worker")
        void inactiveContractIgnored() {
            WorkerActivity activity = activity(List.of(), List.of(), List.of(),
                    null, null, null, false);

            assertThat(engine.evaluate(activity))
                    .noneMatch(d -> d.typology() == Typology.GHOST_WORKER);
        }
    }

    @Nested
    class ProxyClockIn {

        @Test
        @DisplayName("a mocked location plus a weak biometric compounds into high confidence")
        void mockedLocationAndWeakBiometric() {
            WorkerActivity activity = activity(
                    List.of(clock(T0, -1.29, 36.82, 0.41, false, true)),
                    List.of(new WorkerActivity.Shift(T0, T0.plus(Duration.ofHours(6)), 0)),
                    List.of(), null, null, null, true);

            Detection detection = engine.evaluate(activity).stream()
                    .filter(d -> d.ruleCode().equals("PROXY_CLOCK_IN_V1"))
                    .findFirst()
                    .orElseThrow();

            assertThat(detection.confidence()).isEqualTo(1.0);
            assertThat(detection.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(detection.explanation()).contains("mocked location");
            assertThat(detection.evidence()).containsEntry("mockLocation", true);
        }

        @Test
        @DisplayName("a clean biometric inside the geofence raises nothing")
        void cleanClockInIsSilent() {
            WorkerActivity activity = activity(
                    List.of(clock(T0, -1.29, 36.82, 0.95, true, false)),
                    List.of(new WorkerActivity.Shift(T0, T0.plus(Duration.ofHours(6)), 0)),
                    List.of(), null, null, null, true);

            assertThat(engine.evaluate(activity))
                    .noneMatch(d -> d.ruleCode().equals("PROXY_CLOCK_IN_V1"));
        }
    }

    @Nested
    class ImpossibleTravel {

        @Test
        @DisplayName("Nairobi to Nakuru in ten minutes is impossible and is flagged")
        void impossibleSpeedFlagged() {
            WorkerActivity activity = activity(
                    List.of(clock(T0, -1.2921, 36.8219, 0.95, true, false),
                            clock(T0.plus(Duration.ofMinutes(10)), -0.3031, 36.0800, 0.95, true, false)),
                    List.of(), List.of(), null, null, null, true);

            Detection detection = engine.evaluate(activity).stream()
                    .filter(d -> d.ruleCode().equals("IMPOSSIBLE_TRAVEL_V1"))
                    .findFirst()
                    .orElseThrow();

            assertThat(detection.typology()).isEqualTo(Typology.PROXY_CLOCK_IN);
            assertThat((double) detection.evidence().get("impliedSpeedKph")).isGreaterThan(600.0);
        }

        @Test
        @DisplayName("a normal commute between farms is not flagged")
        void plausibleTravelIgnored() {
            WorkerActivity activity = activity(
                    List.of(clock(T0, -1.2921, 36.8219, 0.95, true, false),
                            clock(T0.plus(Duration.ofHours(3)), -1.1000, 36.9000, 0.95, true, false)),
                    List.of(), List.of(), null, null, null, true);

            assertThat(engine.evaluate(activity))
                    .noneMatch(d -> d.ruleCode().equals("IMPOSSIBLE_TRAVEL_V1"));
        }

        @Test
        @DisplayName("two clock events at the same place are never impossible travel")
        void sameLocationIgnored() {
            WorkerActivity activity = activity(
                    List.of(clock(T0, -1.2921, 36.8219, 0.95, true, false),
                            clock(T0.plusSeconds(30), -1.2921, 36.8219, 0.95, true, false)),
                    List.of(), List.of(), null, null, null, true);

            assertThat(engine.evaluate(activity))
                    .noneMatch(d -> d.ruleCode().equals("IMPOSSIBLE_TRAVEL_V1"));
        }
    }

    @Nested
    class PieceRateInflation {

        @Test
        @DisplayName("output far above the peer median is flagged")
        void abovePeerMedianFlagged() {
            WorkerActivity activity = activity(
                    List.of(clock(T0, -1.29, 36.82, 0.95, true, false)),
                    List.of(new WorkerActivity.Shift(T0, T0.plus(Duration.ofHours(8)), 0)),
                    List.of(new WorkerActivity.PieceRate(T0, "HARVEST", new BigDecimal("400"), "kg")),
                    new BigDecimal("100"), null, null, true);

            Detection detection = engine.evaluate(activity).stream()
                    .filter(d -> d.typology() == Typology.PIECE_RATE_INFLATION)
                    .findFirst()
                    .orElseThrow();

            assertThat(detection.explanation()).contains("peer median");
        }

        @Test
        @DisplayName("output above the agronomic ceiling for the plot is near-certain fraud")
        void aboveAgronomicCeilingFlagged() {
            WorkerActivity activity = activity(
                    List.of(clock(T0, -1.29, 36.82, 0.95, true, false)),
                    List.of(new WorkerActivity.Shift(T0, T0.plus(Duration.ofHours(8)), 0)),
                    List.of(new WorkerActivity.PieceRate(T0, "HARVEST", new BigDecimal("5000"), "kg")),
                    null, new BigDecimal("1.0"), new BigDecimal("900"), true);

            Detection detection = engine.evaluate(activity).stream()
                    .filter(d -> d.explanation().contains("agronomic ceiling"))
                    .findFirst()
                    .orElseThrow();

            assertThat(detection.confidence()).isEqualTo(0.92);
            assertThat(detection.severity()).isEqualTo(Severity.CRITICAL);
        }
    }

    @Nested
    class Scoring {

        @Test
        @DisplayName("independent detections compound rather than simply adding")
        void detectionsCompound() {
            List<Detection> two = List.of(
                    Detection.of(Typology.GHOST_WORKER, "A", 0.5, "x", Map.of()),
                    Detection.of(Typology.HOURS_INFLATION, "B", 0.5, "y", Map.of()));

            assertThat(engine.riskScore(two)).isEqualTo(75);
        }

        @Test
        @DisplayName("the score is bounded at 100 no matter how many detections fire")
        void scoreIsBounded() {
            List<Detection> many = List.of(
                    Detection.of(Typology.GHOST_WORKER, "A", 0.99, "x", Map.of()),
                    Detection.of(Typology.PROXY_CLOCK_IN, "B", 0.99, "y", Map.of()),
                    Detection.of(Typology.HOURS_INFLATION, "C", 0.99, "z", Map.of()));

            assertThat(engine.riskScore(many)).isBetween(0, 100);
        }

        @Test
        @DisplayName("detections come back ordered by confidence, strongest first")
        void detectionsAreOrdered() {
            WorkerActivity activity = activity(
                    List.of(clock(T0, -1.29, 36.82, 0.30, false, true)),
                    List.of(new WorkerActivity.Shift(T0, T0.plus(Duration.ofHours(20)), 0)),
                    List.of(), null, null, null, true);

            List<Detection> detections = engine.evaluate(activity);

            assertThat(detections).isNotEmpty();
            assertThat(detections).isSortedAccordingTo(
                    (a, b) -> Double.compare(b.confidence(), a.confidence()));
        }

        @Test
        @DisplayName("a confidence outside [0,1] is rejected at construction")
        void invalidConfidenceRejected() {
            assertThatThrownBy(() ->
                    new Detection(Typology.GHOST_WORKER, "A", 1.4, Severity.HIGH, "x", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Distance {

        @Test
        @DisplayName("the haversine distance matches a known city pair")
        void knownDistance() {
            double km = Geo.distanceKm(-1.2921, 36.8219, -0.3031, 36.0800);
            assertThat(km).isBetween(120.0, 145.0);
        }

        @Test
        @DisplayName("distance from a point to itself is zero")
        void zeroDistance() {
            assertThat(Geo.distanceKm(-1.2921, 36.8219, -1.2921, 36.8219)).isZero();
        }
    }
}
