package com.smartseason.fraud.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DetectionTest {

    @Test
    void evidenceWithAbsentFieldsIsKeptWithoutThem() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("insideGeofence", false);
        evidence.put("biometricScore", null);
        evidence.put("deviceId", null);

        Detection detection = Detection.of(Typology.PROXY_CLOCK_IN, "PROXY_CLOCK", 0.25,
                "clock event fell outside the farm geofence", evidence);

        assertThat(detection.evidence()).containsOnlyKeys("insideGeofence");
    }
}
