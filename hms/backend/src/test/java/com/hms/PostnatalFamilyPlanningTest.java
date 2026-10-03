package com.hms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostnatalFamilyPlanningTest extends IntegrationTest {

    private UUID woman(Org org, String family) throws Exception {
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Mama", family + UUID.randomUUID().toString().substring(0, 5), "1995-03-02")).get("id").asText());
    }

    private String pregnancy(Org org, String nurse, UUID mother, LocalDate lmp) throws Exception {
        return sendJson(post("/v1/mch/pregnancies", nurse), Map.of("facilityId", org.facilityId().toString(), "patientId", mother.toString(), "lmp", lmp.toString(),
                "gravida", 2, "parity", 1), 201).get("id").asText();
    }

    private Map<String, Object> fp(Org org, String type, String method, Object... extra) {
        Map<String, Object> m = new HashMap<>(Map.of("facilityId", org.facilityId().toString(), "visitType", type, "method", method));
        for (int i = 0; i + 1 < extra.length; i += 2) {
            m.put((String) extra[i], extra[i + 1]);
        }
        return m;
    }

    @Test
    void postnatalCareStartsAfterTheOutcomeAndFlagsWhatTheReadingsShow() throws Exception {
        Org org = newOrg("pnc");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        UUID mother = woman(org, "Wangari");
        String id = pregnancy(org, nurse, mother, LocalDate.now().minusDays(270));

        // Nothing to record until the pregnancy has an outcome.
        sendJson(post("/v1/mch/pregnancies/" + id + "/postnatal", nurse), Map.of("systolic", 110, "diastolic", 70), 409);
        send(get("/v1/mch/pregnancies/" + id + "/postnatal", nurse), 409);

        LocalDate delivered = LocalDate.now().minusDays(10);
        sendJson(post("/v1/mch/pregnancies/" + id + "/delivery", nurse), Map.of("deliveredOn", delivered.toString(), "mode", "SVD", "outcome", "LIVE_BIRTH"), 200);

        Map<String, Object> v1 = new HashMap<>();
        for (Object[] kv : new Object[][] {{"visitedOn", delivered.plusDays(3).toString()}, {"systolic", 150}, {"diastolic", 95}, {"temperatureC", 38.4},
                {"lochia", "OFFENSIVE"}, {"wound", "INFECTED"}, {"lowMood", true}, {"babyTemperatureC", 38.0}, {"cord", "INFECTED"}, {"jaundice", true},
                {"feedingWell", false}, {"nextVisitOn", LocalDate.now().minusDays(1).toString()}}) {
            v1.put((String) kv[0], kv[1]);
        }
        JsonNode after = sendJson(post("/v1/mch/pregnancies/" + id + "/postnatal", nurse), v1, 201);
        assertThat(after.get("visits")).hasSize(1);
        assertThat(after.get("visits").get(0).get("daysSinceDelivery").asInt()).isEqualTo(3);
        String codes = after.get("visits").get(0).get("flags").toString();
        assertThat(codes).contains("HYPERTENSION", "MATERNAL_FEVER", "OFFENSIVE_LOCHIA", "WOUND_INFECTION", "LOW_MOOD", "NEWBORN_FEVER", "CORD_INFECTION", "JAUNDICE", "POOR_FEEDING");
        assertThat(after.get("overdue").asBoolean()).isTrue();
        assertThat(after.get("babyRecorded").asBoolean()).isTrue();

        // Dates and readings that cannot be true are refused, and visits stay in order.
        sendJson(post("/v1/mch/pregnancies/" + id + "/postnatal", nurse), Map.of("visitedOn", delivered.minusDays(1).toString()), 400);
        sendJson(post("/v1/mch/pregnancies/" + id + "/postnatal", nurse), Map.of("visitedOn", LocalDate.now().plusDays(1).toString()), 400);
        sendJson(post("/v1/mch/pregnancies/" + id + "/postnatal", nurse), Map.of("systolic", 110), 400);
        sendJson(post("/v1/mch/pregnancies/" + id + "/postnatal", nurse), Map.of("visitedOn", delivered.plusDays(1).toString()), 409);
        JsonNode v2 = sendJson(post("/v1/mch/pregnancies/" + id + "/postnatal", nurse),
                Map.of("systolic", 118, "diastolic", 76, "temperatureC", 36.8, "babyTemperatureC", 36.9, "feedingWell", true, "cord", "SEPARATED"), 201);
        assertThat(v2.get("visits").get(1).get("visitNumber").asInt()).isEqualTo(2);
        assertThat(v2.get("visits").get(1).get("flags")).isEmpty();
        assertThat(fetch("/v1/mch/pregnancies/" + id + "/postnatal", nurse).get("scheduleNote").asText()).contains("WHO");
    }

    @Test
    void theSixWeekPeriodAndTheLiveBirthRuleAreEnforced() throws Exception {
        Org org = newOrg("pnc2");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        String lost = pregnancy(org, nurse, woman(org, "Akinyi"), LocalDate.now().minusDays(80));
        sendJson(post("/v1/mch/pregnancies/" + lost + "/delivery", nurse), Map.of("deliveredOn", LocalDate.now().minusDays(5).toString(), "mode", "NOT_APPLICABLE", "outcome", "MISCARRIAGE"), 200);
        // Baby readings make no sense after a miscarriage; the mother's check still does.
        sendJson(post("/v1/mch/pregnancies/" + lost + "/postnatal", nurse), Map.of("babyWeightG", 3000), 400);
        JsonNode ok = sendJson(post("/v1/mch/pregnancies/" + lost + "/postnatal", nurse), Map.of("systolic", 120, "diastolic", 80), 201);
        assertThat(ok.get("babyRecorded").asBoolean()).isFalse();

        String old = pregnancy(org, nurse, woman(org, "Njeri"), LocalDate.now().minusDays(290));
        sendJson(post("/v1/mch/pregnancies/" + old + "/delivery", nurse), Map.of("deliveredOn", LocalDate.now().minusDays(70).toString(), "mode", "SVD", "outcome", "LIVE_BIRTH"), 200);
        sendJson(post("/v1/mch/pregnancies/" + old + "/postnatal", nurse), Map.of("visitedOn", LocalDate.now().toString()), 400);
        sendJson(post("/v1/mch/pregnancies/" + old + "/postnatal", nurse), Map.of("visitedOn", LocalDate.now().minusDays(20).toString()), 201);
    }

    @Test
    void postnatalAndFamilyPlanningRecordsBelongToOneOrganisationAndNeedThePermission() throws Exception {
        Org a = newOrg("pnca");
        Org b = newOrg("pncb");
        String nurse = userWithRole(a, "NURSE", "NURSE");
        String outsider = userWithRole(b, "NURSE", "NURSE");
        UUID mother = woman(a, "Atieno");
        String id = pregnancy(a, nurse, mother, LocalDate.now().minusDays(270));
        sendJson(post("/v1/mch/pregnancies/" + id + "/delivery", nurse), Map.of("deliveredOn", LocalDate.now().minusDays(4).toString(), "mode", "SVD", "outcome", "LIVE_BIRTH"), 200);
        sendJson(post("/v1/mch/family-planning/patients/" + mother + "/visits", nurse), fp(a, "NEW", "MALE_CONDOM"), 201);

        send(get("/v1/mch/pregnancies/" + id + "/postnatal", outsider), 404);
        send(get("/v1/mch/family-planning/patients/" + mother, outsider), 404);
        sendJson(post("/v1/mch/pregnancies/" + id + "/postnatal", outsider), Map.of("systolic", 120, "diastolic", 80), 404);
        // A pharmacist holds no maternal health permission.
        String pharmacist = userWithRole(a, "PHARMACIST", "PHARMACIST");
        send(get("/v1/mch/pregnancies/" + id + "/postnatal", pharmacist), 403);
        send(get("/v1/mch/family-planning/patients/" + mother, pharmacist), 403);
    }

    @Test
    void familyPlanningTracksTheCurrentMethodAndWhoIsDue() throws Exception {
        Org org = newOrg("fp");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        UUID patient = woman(org, "Chebet");

        assertThat(fetch("/v1/mch/family-planning/patients/" + patient, nurse).has("currentMethod")).isFalse();
        // The pill with raised pressure is allowed but flagged.
        JsonNode start = sendJson(post("/v1/mch/family-planning/patients/" + patient + "/visits", nurse),
                fp(org, "NEW", "COC", "systolic", 150, "diastolic", 95, "visitedOn", LocalDate.now().minusDays(100).toString(), "nextDueOn", LocalDate.now().minusDays(10).toString()), 201);
        assertThat(start.get("currentMethod").asText()).isEqualTo("COC");
        assertThat(start.get("visits").get(0).get("flags").toString()).contains("COC_RAISED_BP");
        assertThat(start.get("overdue").asBoolean()).isTrue();

        // The method rules: no second start, a revisit keeps the method, a switch changes it.
        sendJson(post("/v1/mch/family-planning/patients/" + patient + "/visits", nurse), fp(org, "NEW", "DMPA"), 409);
        sendJson(post("/v1/mch/family-planning/patients/" + patient + "/visits", nurse), fp(org, "REVISIT", "DMPA"), 400);
        sendJson(post("/v1/mch/family-planning/patients/" + patient + "/visits", nurse), fp(org, "SWITCH", "COC"), 400);
        sendJson(post("/v1/mch/family-planning/patients/" + patient + "/visits", nurse), fp(org, "DISCONTINUE", "COC"), 400);
        sendJson(post("/v1/mch/family-planning/patients/" + patient + "/visits", nurse), fp(org, "REVISIT", "COC", "visitedOn", LocalDate.now().minusDays(200).toString()), 409);

        JsonNode due = fetch("/v1/mch/family-planning/due?overdueOnly=true", nurse);
        assertThat(due.get("data")).hasSize(1);
        assertThat(due.get("data").get(0).get("daysOverdue").asInt()).isEqualTo(10);

        // An injectable gets its date from the 13-week interval when none is given, and leaves the overdue list.
        JsonNode sw = sendJson(post("/v1/mch/family-planning/patients/" + patient + "/visits", nurse), fp(org, "SWITCH", "DMPA"), 201);
        assertThat(sw.get("currentMethod").asText()).isEqualTo("DMPA");
        assertThat(sw.get("nextDueOn").asText()).isEqualTo(LocalDate.now().plusDays(91).toString());
        assertThat(sw.get("overdue").asBoolean()).isFalse();
        assertThat(fetch("/v1/mch/family-planning/due?overdueOnly=true", nurse).get("data")).isEmpty();
        assertThat(fetch("/v1/mch/family-planning/due?horizonDays=90", nurse).get("data")).as("91 days out is beyond the 90-day horizon").isEmpty();

        JsonNode stop = sendJson(post("/v1/mch/family-planning/patients/" + patient + "/visits", nurse), fp(org, "DISCONTINUE", "NONE", "notes", "Wants a baby"), 201);
        assertThat(stop.has("currentMethod")).isFalse();
        assertThat(stop.get("visits")).hasSize(3);
        assertThat(fetch("/v1/mch/family-planning/due?horizonDays=90", nurse).get("data")).isEmpty();
        sendJson(post("/v1/mch/family-planning/patients/" + patient + "/visits", nurse), fp(org, "DISCONTINUE", "NONE"), 409);
    }

    @Test
    void familyPlanningRefusesWhatDoesNotApply() throws Exception {
        Org org = newOrg("fp2");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        UUID man = UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Juma", "Man" + UUID.randomUUID().toString().substring(0, 5), "1990-01-01", "sex", "MALE")).get("id").asText());
        sendJson(post("/v1/mch/family-planning/patients/" + man + "/visits", nurse), fp(org, "NEW", "IMPLANT"), 400);
        sendJson(post("/v1/mch/family-planning/patients/" + man + "/visits", nurse), fp(org, "NEW", "VASECTOMY"), 201);
        UUID woman = woman(org, "Wafula");
        sendJson(post("/v1/mch/family-planning/patients/" + woman + "/visits", nurse), fp(org, "NEW", "VASECTOMY"), 400);
        // A permanent method has no repeat contact, so a date makes no sense.
        sendJson(post("/v1/mch/family-planning/patients/" + woman + "/visits", nurse), fp(org, "NEW", "TUBAL_LIGATION", "nextDueOn", LocalDate.now().plusDays(30).toString()), 400);
        sendJson(post("/v1/mch/family-planning/patients/" + woman + "/visits", nurse), fp(org, "NEW", "NONE"), 400);
        sendJson(post("/v1/mch/family-planning/patients/" + woman + "/visits", nurse), fp(org, "NEW", "COC", "visitedOn", LocalDate.now().plusDays(1).toString()), 400);

        // During a pregnancy a hormonal method or IUD cannot be started, but a condom can.
        UUID expecting = woman(org, "Mutua");
        pregnancy(org, nurse, expecting, LocalDate.now().minusDays(60));
        sendJson(post("/v1/mch/family-planning/patients/" + expecting + "/visits", nurse), fp(org, "NEW", "IUCD"), 409);
        sendJson(post("/v1/mch/family-planning/patients/" + expecting + "/visits", nurse), fp(org, "NEW", "MALE_CONDOM"), 201);
    }

    @Test
    void visitsCannotBeEditedOrDeletedEvenByTheOwner() throws Exception {
        Org org = newOrg("fp3");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        UUID patient = woman(org, "Kosgei");
        sendJson(post("/v1/mch/family-planning/patients/" + patient + "/visits", nurse), fp(org, "NEW", "POP"), 201);
        assertThrows(Exception.class, () -> asOwner("UPDATE family_planning_visits SET method = 'COC' WHERE patient_id = '" + patient + "'"));
        assertThrows(Exception.class, () -> asOwner("DELETE FROM family_planning_visits WHERE patient_id = '" + patient + "'"));
        assertThat(fetch("/v1/mch/family-planning/patients/" + patient, nurse).get("currentMethod").asText()).isEqualTo("POP");
    }
}
