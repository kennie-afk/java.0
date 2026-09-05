package com.smartseason.attendance.clockin;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartseason.attendance.domain.Geofence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GeofenceEvaluatorTest {

    private static final double FARM_LAT = -0.3286;
    private static final double FARM_LNG = 35.9403;

    private final GeofenceEvaluator evaluator = new GeofenceEvaluator(ClockInPolicy.defaults());
    private final UUID worker = UUID.randomUUID();
    private final UUID farm = UUID.randomUUID();

    private Geofence fence(double lat, double lng, int radiusM, boolean active) {
        Geofence fence = new Geofence();
        fence.setId(UUID.randomUUID());
        fence.setFarmId(farm);
        fence.setName("Main gate");
        fence.setCenterLat(BigDecimal.valueOf(lat));
        fence.setCenterLng(BigDecimal.valueOf(lng));
        fence.setRadiusM(radiusM);
        fence.setActive(active);
        return fence;
    }

    private ClockInAttempt attempt(Double lat, Double lng, Double accuracy,
                                   Double biometric, boolean mocked) {
        return new ClockInAttempt(worker, farm, Instant.now(), lat, lng, accuracy,
                biometric, mocked, "device-1");
    }

    @Test
    @DisplayName("a worker standing inside the geofence with a good biometric is accepted")
    void insideGeofenceIsAccepted() {
        ClockInDecision decision = evaluator.evaluate(
                attempt(FARM_LAT, FARM_LNG, 8.0, 0.95, false),
                List.of(fence(FARM_LAT, FARM_LNG, 200, true)));

        assertThat(decision.accepted()).isTrue();
        assertThat(decision.insideGeofence()).isTrue();
        assertThat(decision.matchedGeofenceId()).isNotNull();
        assertThat(decision.distanceM()).isLessThan(1.0);
        assertThat(decision.reasons()).isEmpty();
    }

    @Nested
    class Rejection {

        @Test
        @DisplayName("a mocked location is rejected outright, before anything else is considered")
        void mockedLocationIsRejected() {
            ClockInDecision decision = evaluator.evaluate(
                    attempt(FARM_LAT, FARM_LNG, 5.0, 0.99, true),
                    List.of(fence(FARM_LAT, FARM_LNG, 200, true)));

            assertThat(decision.verdict()).isEqualTo(ClockInDecision.Verdict.REJECTED);
            assertThat(decision.reasonSummary()).contains("mocked location");
        }

        @Test
        @DisplayName("clocking in from far outside the farm is rejected, not merely flagged")
        void outsideGeofenceIsRejected() {
            ClockInDecision decision = evaluator.evaluate(
                    attempt(-1.2921, 36.8219, 8.0, 0.95, false),
                    List.of(fence(FARM_LAT, FARM_LNG, 200, true)));

            assertThat(decision.verdict()).isEqualTo(ClockInDecision.Verdict.REJECTED);
            assertThat(decision.insideGeofence()).isFalse();
            assertThat(decision.reasonSummary()).contains("outside every active geofence");
        }

        @Test
        @DisplayName("an inactive geofence does not admit anyone")
        void inactiveGeofenceDoesNotMatch() {
            ClockInDecision decision = evaluator.evaluate(
                    attempt(FARM_LAT, FARM_LNG, 8.0, 0.95, false),
                    List.of(fence(FARM_LAT, FARM_LNG, 200, false)));

            assertThat(decision.verdict()).isEqualTo(ClockInDecision.Verdict.REJECTED);
        }

        @Test
        @DisplayName("a missing position is rejected when a geofence is required")
        void missingPositionIsRejected() {
            ClockInDecision decision = evaluator.evaluate(
                    attempt(null, null, null, 0.95, false),
                    List.of(fence(FARM_LAT, FARM_LNG, 200, true)));

            assertThat(decision.verdict()).isEqualTo(ClockInDecision.Verdict.REJECTED);
            assertThat(decision.reasonSummary()).contains("no position supplied");
        }

        @Test
        @DisplayName("a farm with no geofences configured rejects rather than admitting everyone")
        void noGeofencesRejects() {
            ClockInDecision decision = evaluator.evaluate(
                    attempt(FARM_LAT, FARM_LNG, 8.0, 0.95, false), List.of());

            assertThat(decision.verdict()).isEqualTo(ClockInDecision.Verdict.REJECTED);
        }
    }

    @Nested
    class Flagging {

        @Test
        @DisplayName("a weak biometric inside the fence is flagged for review, not rejected")
        void weakBiometricIsFlagged() {
            ClockInDecision decision = evaluator.evaluate(
                    attempt(FARM_LAT, FARM_LNG, 8.0, 0.55, false),
                    List.of(fence(FARM_LAT, FARM_LNG, 200, true)));

            assertThat(decision.verdict()).isEqualTo(ClockInDecision.Verdict.FLAGGED);
            assertThat(decision.insideGeofence()).isTrue();
            assertThat(decision.reasonSummary()).contains("biometric match");
        }

        @Test
        @DisplayName("a poor GPS fix inside the fence is flagged, because the worker may be genuine")
        void poorAccuracyIsFlagged() {
            ClockInDecision decision = evaluator.evaluate(
                    attempt(FARM_LAT, FARM_LNG, 350.0, 0.95, false),
                    List.of(fence(FARM_LAT, FARM_LNG, 200, true)));

            assertThat(decision.verdict()).isEqualTo(ClockInDecision.Verdict.FLAGGED);
            assertThat(decision.reasonSummary()).contains("accuracy");
        }
    }

    @Test
    @DisplayName("the tolerance admits a worker just outside the drawn radius")
    void toleranceAdmitsJustOutside() {
        ClockInDecision decision = evaluator.evaluate(
                attempt(FARM_LAT + 0.00025, FARM_LNG, 8.0, 0.95, false),
                List.of(fence(FARM_LAT, FARM_LNG, 10, true)));

        assertThat(decision.insideGeofence())
                .as("about 28m from a 10m fence, inside the 50m tolerance")
                .isTrue();
    }

    @Test
    @DisplayName("the nearest matching geofence is the one recorded")
    void nearestFenceWins() {
        Geofence near = fence(FARM_LAT, FARM_LNG, 500, true);
        Geofence far = fence(FARM_LAT + 0.002, FARM_LNG, 500, true);

        ClockInDecision decision = evaluator.evaluate(
                attempt(FARM_LAT, FARM_LNG, 8.0, 0.95, false), List.of(far, near));

        assertThat(decision.matchedGeofenceId()).isEqualTo(near.getId());
    }

    @Test
    @DisplayName("a permissive policy flags an out-of-fence clock-in instead of rejecting it")
    void permissivePolicyFlagsInstead() {
        var permissive = new GeofenceEvaluator(
                new ClockInPolicy(0.80, 100.0, 50.0, true, false));

        ClockInDecision decision = permissive.evaluate(
                attempt(-1.2921, 36.8219, 8.0, 0.95, false),
                List.of(fence(FARM_LAT, FARM_LNG, 200, true)));

        assertThat(decision.verdict()).isEqualTo(ClockInDecision.Verdict.FLAGGED);
    }

    @Test
    @DisplayName("distance from a point to itself is zero")
    void zeroDistance() {
        assertThat(Geo.distanceMetres(FARM_LAT, FARM_LNG, FARM_LAT, FARM_LNG)).isZero();
    }
}
