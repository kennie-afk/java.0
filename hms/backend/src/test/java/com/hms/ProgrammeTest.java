package com.hms;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProgrammeTest extends IntegrationTest {

    private UUID newPatient(Org org) throws Exception {
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Prog", "Pat" + UUID.randomUUID().toString().substring(0, 6), "1980-01-01")).get("id").asText());
    }

    private Map<String, Object> enrol(Org org, UUID patient, String programme, Object... extra) {
        Map<String, Object> m = new java.util.LinkedHashMap<>(Map.of("facilityId", org.facilityId().toString(), "patientId", patient.toString(), "programme", programme));
        for (int i = 0; i < extra.length; i += 2) {
            m.put((String) extra[i], extra[i + 1]);
        }
        return m;
    }

    @Test
    void enrolmentVisitsAndOutcomesFollowTheRules() throws Exception {
        Org org = newOrg("prog");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        UUID p = newPatient(org);
        LocalDate today = LocalDate.now();
        JsonNode e = create("/v1/programmes/enrolments", nurse, enrol(org, p, "HYPERTENSION", "regimen", "Amlodipine 5 mg", "nextVisitOn", today.plusDays(30).toString()));
        String id = e.get("id").asText();
        assertThat(e.get("registerNo").asText()).isEqualTo("HYPERTENSION-00001");
        // One active enrolment per programme.
        sendJson(post("/v1/programmes/enrolments", nurse), enrol(org, p, "HYPERTENSION"), 409);
        sendJson(post("/v1/programmes/enrolments", nurse), enrol(org, p, "ASTHMA", "enrolledOn", today.plusDays(1).toString()), 400);
        sendJson(post("/v1/programmes/enrolments", nurse), enrol(org, p, "NONSENSE"), 400);
        // Visit validation: future date, half a blood pressure, systolic below diastolic, next visit not after the visit.
        sendJson(post("/v1/programmes/enrolments/" + id + "/visits", nurse), Map.of("visitedOn", today.plusDays(1).toString()), 400);
        sendJson(post("/v1/programmes/enrolments/" + id + "/visits", nurse), Map.of("visitedOn", today.toString(), "systolic", 140), 400);
        sendJson(post("/v1/programmes/enrolments/" + id + "/visits", nurse), Map.of("visitedOn", today.toString(), "systolic", 80, "diastolic", 120), 400);
        sendJson(post("/v1/programmes/enrolments/" + id + "/visits", nurse), Map.of("visitedOn", today.toString(), "nextVisitOn", today.toString()), 400);
        JsonNode v = sendJson(post("/v1/programmes/enrolments/" + id + "/visits", nurse), Map.of("visitedOn", today.toString(), "systolic", 146, "diastolic", 92, "weightKg", 78.5,
                "adherence", "GOOD", "regimen", "Amlodipine 10 mg", "nextVisitOn", today.plusDays(28).toString()), 201);
        assertThat(v.get("regimen").asText()).isEqualTo("Amlodipine 10 mg");
        assertThat(v.get("nextVisitOn").asText()).isEqualTo(today.plusDays(28).toString());
        assertThat(v.get("visits")).hasSize(1);
        // The outcome closes the enrolment, clears the appointment, and a new enrolment is then allowed.
        sendJson(post("/v1/programmes/enrolments/" + id + "/outcome", nurse), Map.of("status", "TRANSFERRED_OUT", "note", "x"), 400);
        JsonNode closed = sendJson(post("/v1/programmes/enrolments/" + id + "/outcome", nurse), Map.of("status", "TRANSFERRED_OUT", "note", "Moved to Kisumu County Hospital"), 200);
        assertThat(closed.get("status").asText()).isEqualTo("TRANSFERRED_OUT");
        assertThat(closed.has("nextVisitOn")).isFalse();
        sendJson(post("/v1/programmes/enrolments/" + id + "/outcome", nurse), Map.of("status", "STOPPED", "note", "again please"), 409);
        sendJson(post("/v1/programmes/enrolments/" + id + "/visits", nurse), Map.of("visitedOn", today.toString()), 409);
        JsonNode again = create("/v1/programmes/enrolments", nurse, enrol(org, p, "HYPERTENSION"));
        assertThat(again.get("registerNo").asText()).isEqualTo("HYPERTENSION-00002");
        assertThat(fetch("/v1/programmes/enrolments?patientId=" + p, nurse).get("data")).hasSize(2);
        assertThat(fetch("/v1/audit/verify", org.token()).findValuesAsText("intact")).containsOnly("true");
    }

    @Test
    void defaultersAreThoseOverduePastTheGracePeriodAndCountsAreAggregates() throws Exception {
        Org org = newOrg("defaulters");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        LocalDate today = LocalDate.now();
        UUID late = newPatient(org);
        UUID recent = newPatient(org);
        UUID fine = newPatient(org);
        create("/v1/programmes/enrolments", nurse, enrol(org, late, "TB", "enrolledOn", today.minusDays(60).toString(), "nextVisitOn", today.minusDays(20).toString()));
        create("/v1/programmes/enrolments", nurse, enrol(org, recent, "TB", "enrolledOn", today.minusDays(60).toString(), "nextVisitOn", today.minusDays(3).toString()));
        create("/v1/programmes/enrolments", nurse, enrol(org, fine, "DIABETES", "nextVisitOn", today.plusDays(10).toString()));
        JsonNode d = fetch("/v1/programmes/defaulters?facilityId=" + org.facilityId(), nurse);
        assertThat(d).hasSize(1);
        assertThat(d.get(0).get("daysOverdue").asInt()).isEqualTo(20);
        assertThat(fetch("/v1/programmes/defaulters?facilityId=" + org.facilityId() + "&graceDays=1", nurse)).hasSize(2);
        assertThat(fetch("/v1/programmes/defaulters?facilityId=" + org.facilityId() + "&programme=DIABETES&graceDays=0", nurse)).isEmpty();
        send(get("/v1/programmes/defaulters?facilityId=" + org.facilityId() + "&graceDays=-1", nurse), 400);
        JsonNode s = fetch("/v1/programmes/summary?facilityId=" + org.facilityId(), nurse);
        JsonNode tb = null;
        for (JsonNode c : s.get("programmes")) {
            if (c.get("programme").asText().equals("TB")) {
                tb = c;
            }
        }
        assertThat(tb.get("active").asInt()).isEqualTo(2);
        assertThat(tb.get("missedVisit").asInt()).isEqualTo(1);
        assertThat(s.toString()).doesNotContain("Prog");
        // A listed enrolment shows how late it is.
        assertThat(fetch("/v1/programmes/enrolments?programme=TB", nurse).get("data").findValues("daysOverdue")).extracting(JsonNode::asInt).containsExactlyInAnyOrder(20, 3);
    }

    @Test
    void hivCareNeedsItsOwnPermissionAndIsAuditedWhenOpened() throws Exception {
        Org org = newOrg("hiv");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        String auditor = userWithRole(org, "AUDITOR", "ADMINISTRATIVE");
        UUID p = newPatient(org);
        JsonNode e = create("/v1/programmes/enrolments", nurse, enrol(org, p, "HIV", "regimen", "First-line, fixed-dose", "nextVisitOn", LocalDate.now().plusDays(30).toString()));
        String id = e.get("id").asText();
        // The auditor reads programmes in general but sees no HIV rows, cannot open one and cannot write one.
        assertThat(fetch("/v1/programmes/enrolments", auditor).get("data")).isEmpty();
        send(get("/v1/programmes/enrolments/" + id, auditor), 403);
        sendJson(post("/v1/programmes/enrolments", auditor), enrol(org, p, "TB"), 403);
        assertThat(fetch("/v1/programmes/enrolments", nurse).get("data")).hasSize(1);
        assertThat(fetch("/v1/programmes/summary?facilityId=" + org.facilityId(), auditor).get("programmes")).isEmpty();
        fetch("/v1/programmes/enrolments/" + id, nurse);
        assertThat(fetch("/v1/audit/events?entityType=patient&entityId=" + p, org.token()).findValuesAsText("action")).contains("programme.enrol", "programme.view");
    }

    @Test
    void enrolmentsAreScopedToTheirOrganisation() throws Exception {
        Org a = newOrg("prog-a");
        Org b = newOrg("prog-b");
        UUID pa = newPatient(a);
        String id = create("/v1/programmes/enrolments", a.token(), enrol(a, pa, "ASTHMA")).get("id").asText();
        send(get("/v1/programmes/enrolments/" + id, b.token()), 404);
        assertThat(fetch("/v1/programmes/enrolments", b.token()).get("data")).isEmpty();
        sendJson(post("/v1/programmes/enrolments", b.token()), enrol(b, pa, "ASTHMA"), 404);
    }
}
