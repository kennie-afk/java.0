package com.hms;

import static org.assertj.core.api.Assertions.assertThat;

import com.hms.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Lists returned whole (wards, beds, clinics, lab tests) refuse to grow past the cap instead of silently truncating or slowing down. */
class ShortListCapTest extends IntegrationTest {

    @DynamicPropertySource
    static void cap(DynamicPropertyRegistry r) {
        r.add("hms.limits.catalogue-cap", () -> "3");
    }

    @Test
    void wardsBedsClinicsAndTestsStopAtTheCapWithAClearError() throws Exception {
        Org org = newOrg("cap");
        String f = org.facilityId().toString();
        for (int i = 0; i < 3; i++) {
            create("/v1/inpatient/wards", org.token(), Map.of("facilityId", f, "name", "Ward " + i, "kind", "MEDICAL", "bedLabels", List.of("A", "B", "C")));
        }
        assertThat(fetch("/v1/inpatient/wards?facilityId=" + f, org.token())).hasSize(3);
        String wardId = fetch("/v1/inpatient/wards?facilityId=" + f, org.token()).get(0).get("id").asText();
        assertThat(fetch("/v1/inpatient/wards/" + wardId + "/beds", org.token())).hasSize(3);
        send(post("/v1/inpatient/wards/" + wardId + "/beds", org.token()).content(json.writeValueAsString(Map.of("labels", List.of("D")))), 200);
        assertThat(send(get("/v1/inpatient/wards/" + wardId + "/beds", org.token()), 422).get("code").asText()).isEqualTo("list_too_large");
        create("/v1/inpatient/wards", org.token(), Map.of("facilityId", f, "name", "Ward 4", "kind", "MEDICAL", "bedLabels", List.of("A")));
        JsonNodeHolder.assertTooLarge(send(get("/v1/inpatient/wards?facilityId=" + f, org.token()), 422));

        for (int i = 0; i < 3; i++) {
            create("/v1/lab/tests", org.token(), Map.of("code", "T" + i, "name", "Test " + i, "unit", "g/dL", "price", 100));
        }
        assertThat(fetch("/v1/lab/tests", org.token())).hasSize(3);
        create("/v1/lab/tests", org.token(), Map.of("code", "T9", "name", "Test 9", "unit", "g/dL", "price", 100));
        JsonNodeHolder.assertTooLarge(send(get("/v1/lab/tests", org.token()), 422));
        // Searching narrows it back under the cap.
        assertThat(fetch("/v1/lab/tests?q=T9", org.token())).hasSize(1);

        for (int i = 0; i < 4; i++) {
            create("/v1/scheduling/clinics", org.token(), Map.of("facilityId", f, "name", "Clinic " + i, "specialty", "General", "slotMinutes", 20,
                    "sessions", List.of(Map.of("practitionerId", org.adminId().toString(), "weekday", 1, "startTime", "08:00:00", "endTime", "12:00:00"))));
            if (i < 3) {
                assertThat(fetch("/v1/scheduling/clinics?facilityId=" + f, org.token())).hasSize(i + 1);
            }
        }
        JsonNodeHolder.assertTooLarge(send(get("/v1/scheduling/clinics?facilityId=" + f, org.token()), 422));
    }

    private static final class JsonNodeHolder {
        static void assertTooLarge(com.fasterxml.jackson.databind.JsonNode problem) {
            assertThat(problem.get("code").asText()).isEqualTo("list_too_large");
            assertThat(problem.get("detail").asText()).contains("more than 3");
        }
    }
}
