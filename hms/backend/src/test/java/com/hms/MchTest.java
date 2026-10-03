package com.hms;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MchTest extends IntegrationTest {

    private UUID woman(Org org, String family, String birth) throws Exception {
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Mama", family + UUID.randomUUID().toString().substring(0, 5), birth)).get("id").asText());
    }

    private UUID child(Org org, String family, LocalDate birth) throws Exception {
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Baby", family + UUID.randomUUID().toString().substring(0, 5), birth.toString())).get("id").asText());
    }

    private Map<String, Object> open(Org org, UUID patient, LocalDate lmp) {
        return Map.of("facilityId", org.facilityId().toString(), "patientId", patient.toString(), "lmp", lmp.toString(), "gravida", 2, "parity", 1);
    }

    private Map<String, Object> dose(Org org, String vaccine, LocalDate on) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("facilityId", org.facilityId().toString());
        m.put("vaccine", vaccine);
        if (on != null) {
            m.put("givenOn", on.toString());
        }
        return m;
    }

    @Test
    void aPregnancyRunsFromOpeningThroughVisitsToDelivery() throws Exception {
        Org org = newOrg("anc");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        UUID mother = woman(org, "Wanjiru", "1996-04-10");
        LocalDate lmp = LocalDate.now().minusDays(150);

        JsonNode pr = sendJson(post("/v1/mch/pregnancies", nurse), open(org, mother, lmp), 201);
        String id = pr.get("id").asText();
        assertThat(pr.get("edd").asText()).isEqualTo(lmp.plusDays(280).toString());
        assertThat(pr.get("gestationWeeks").asInt()).isEqualTo(21);
        assertThat(pr.get("status").asText()).isEqualTo("ACTIVE");
        // One ongoing pregnancy per patient.
        sendJson(post("/v1/mch/pregnancies", nurse), open(org, mother, lmp), 409);

        // First visit: raised pressure with protein, low haemoglobin. The server works out the flags.
        Map<String, Object> v1 = new HashMap<>(Map.of("visitedOn", LocalDate.now().minusDays(20).toString(), "systolic", 150, "diastolic", 96,
                "urineProtein", "2+", "haemoglobin", 6.5, "fetalHeartRate", 142, "nextVisitOn", LocalDate.now().minusDays(2).toString()));
        JsonNode after = sendJson(post("/v1/mch/pregnancies/" + id + "/visits", nurse), v1, 201);
        assertThat(after.get("visitCount").asInt()).isEqualTo(1);
        String codes = after.get("visits").get(0).get("flags").toString();
        assertThat(codes).contains("HYPERTENSION").contains("PRE_ECLAMPSIA_SIGNS").contains("SEVERE_ANAEMIA");
        assertThat(after.get("visits").get(0).get("gestationWeeks").asInt()).isEqualTo(18);
        // The pregnancy summary carries the latest flags and, with the next-visit date past, is overdue.
        assertThat(after.get("overdue").asBoolean()).isTrue();
        assertThat(fetch("/v1/mch/pregnancies?overdue=true", nurse).get("data")).hasSize(1);

        // Visits are in date order, and a client cannot dictate the flags.
        sendJson(post("/v1/mch/pregnancies/" + id + "/visits", nurse), Map.of("visitedOn", LocalDate.now().minusDays(40).toString()), 409);
        sendJson(post("/v1/mch/pregnancies/" + id + "/visits", nurse), Map.of("systolic", 110), 400);
        sendJson(post("/v1/mch/pregnancies/" + id + "/visits", nurse), Map.of("systolic", 80, "diastolic", 90), 400);
        sendJson(post("/v1/mch/pregnancies/" + id + "/visits", nurse), Map.of("visitedOn", lmp.minusDays(1).toString()), 400);
        JsonNode v2 = sendJson(post("/v1/mch/pregnancies/" + id + "/visits", nurse), Map.of("systolic", 118, "diastolic", 74, "haemoglobin", 12.1, "iptpGiven", true), 201);
        assertThat(v2.get("visits")).hasSize(2);
        assertThat(v2.get("visits").get(1).get("visitNumber").asInt()).isEqualTo(2);
        assertThat(v2.get("visits").get(1).get("flags")).isEmpty();

        // Delivery closes the pregnancy; a stillbirth cannot carry an Apgar score; nothing more can be added.
        sendJson(post("/v1/mch/pregnancies/" + id + "/delivery", nurse), Map.of("deliveredOn", LocalDate.now().plusDays(1).toString(), "mode", "SVD", "outcome", "LIVE_BIRTH"), 400);
        sendJson(post("/v1/mch/pregnancies/" + id + "/delivery", nurse), Map.of("deliveredOn", LocalDate.now().toString(), "mode", "SVD", "outcome", "STILLBIRTH", "apgar5", 3), 400);
        JsonNode done = sendJson(post("/v1/mch/pregnancies/" + id + "/delivery", nurse),
                Map.of("deliveredOn", LocalDate.now().toString(), "mode", "SVD", "outcome", "LIVE_BIRTH", "birthWeightG", 3200, "apgar5", 9), 200);
        assertThat(done.get("status").asText()).isEqualTo("DELIVERED");
        assertThat(done.get("delivery").get("birthWeightG").asInt()).isEqualTo(3200);
        assertThat(done.get("overdue").asBoolean()).isFalse();
        sendJson(post("/v1/mch/pregnancies/" + id + "/visits", nurse), Map.of("systolic", 120, "diastolic", 80), 409);
        sendJson(post("/v1/mch/pregnancies/" + id + "/delivery", nurse), Map.of("deliveredOn", LocalDate.now().toString(), "mode", "SVD", "outcome", "LIVE_BIRTH"), 409);
        // A new pregnancy is possible once the last one is closed.
        sendJson(post("/v1/mch/pregnancies", nurse), open(org, mother, LocalDate.now().minusDays(10)), 201);
    }

    @Test
    void aMiscarriageClosesTheRecordAsLost() throws Exception {
        Org org = newOrg("loss");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        UUID mother = woman(org, "Achieng", "1990-01-01");
        String id = sendJson(post("/v1/mch/pregnancies", nurse), open(org, mother, LocalDate.now().minusDays(70)), 201).get("id").asText();
        sendJson(post("/v1/mch/pregnancies/" + id + "/delivery", nurse), Map.of("deliveredOn", LocalDate.now().toString(), "mode", "NOT_APPLICABLE", "outcome", "MISCARRIAGE", "babies", 1), 400);
        JsonNode done = sendJson(post("/v1/mch/pregnancies/" + id + "/delivery", nurse), Map.of("deliveredOn", LocalDate.now().toString(), "mode", "NOT_APPLICABLE", "outcome", "MISCARRIAGE"), 200);
        assertThat(done.get("status").asText()).isEqualTo("LOST");
        assertThat(done.get("delivery").get("babies").asInt()).isZero();
    }

    @Test
    void thePregnancyRulesRefuseWhatCannotBeTrue() throws Exception {
        Org org = newOrg("rules");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        UUID mother = woman(org, "Otieno", "1996-04-10");
        // Parity counts earlier births, so it must be below gravida.
        sendJson(post("/v1/mch/pregnancies", nurse), Map.of("facilityId", org.facilityId().toString(), "patientId", mother.toString(),
                "lmp", LocalDate.now().minusDays(50).toString(), "gravida", 2, "parity", 2), 400);
        sendJson(post("/v1/mch/pregnancies", nurse), open(org, mother, LocalDate.now().plusDays(3)), 400);
        sendJson(post("/v1/mch/pregnancies", nurse), open(org, mother, LocalDate.now().minusDays(400)), 400);
        // A man cannot be pregnant; a child of nine cannot have an antenatal record.
        UUID man = UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Juma", "Male" + UUID.randomUUID().toString().substring(0, 5), "1990-01-01", "sex", "MALE")).get("id").asText());
        sendJson(post("/v1/mch/pregnancies", nurse), open(org, man, LocalDate.now().minusDays(50)), 400);
        UUID girl = woman(org, "Young", LocalDate.now().minusYears(9).toString());
        sendJson(post("/v1/mch/pregnancies", nurse), open(org, girl, LocalDate.now().minusDays(50)), 400);
    }

    @Test
    void theCardTracksDosesInOrderAndRefusesWhatIsOutOfSequence() throws Exception {
        Org org = newOrg("epi");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        LocalDate born = LocalDate.now().minusWeeks(16);
        UUID baby = child(org, "Kamau", born);

        JsonNode card = fetch("/v1/mch/immunisation/patients/" + baby, nurse);
        assertThat(card.get("given").asInt()).isZero();
        assertThat(card.get("total").asInt()).isEqualTo(16);
        assertThat(card.get("scheduleNote").asText()).contains("Confirm");
        // A 16-week-old is overdue for everything up to 14 weeks and upcoming for measles-rubella.
        assertThat(status(card, "BCG")).isEqualTo("OVERDUE");
        assertThat(status(card, "PENTA_3")).isEqualTo("DUE");
        assertThat(status(card, "MR_1")).isEqualTo("UPCOMING");

        // Later doses need the earlier one; unknown vaccines and impossible dates are refused.
        sendJson(post("/v1/mch/immunisation/patients/" + baby + "/doses", nurse), dose(org, "PENTA_2", null), 409);
        sendJson(post("/v1/mch/immunisation/patients/" + baby + "/doses", nurse), dose(org, "CHOLERA", null), 400);
        sendJson(post("/v1/mch/immunisation/patients/" + baby + "/doses", nurse), dose(org, "BCG", born.minusDays(1)), 400);
        sendJson(post("/v1/mch/immunisation/patients/" + baby + "/doses", nurse), dose(org, "BCG", LocalDate.now().plusDays(1)), 400);

        JsonNode one = sendJson(post("/v1/mch/immunisation/patients/" + baby + "/doses", nurse), dose(org, "penta_1", born.plusWeeks(6)), 201);
        assertThat(status(one, "PENTA_1")).isEqualTo("GIVEN");
        assertThat(one.get("given").asInt()).isEqualTo(1);
        sendJson(post("/v1/mch/immunisation/patients/" + baby + "/doses", nurse), dose(org, "PENTA_1", born.plusWeeks(6)), 409);
        // Doses of a series are at least four weeks apart.
        sendJson(post("/v1/mch/immunisation/patients/" + baby + "/doses", nurse), dose(org, "PENTA_2", born.plusWeeks(6).plusDays(27)), 409);
        JsonNode two = sendJson(post("/v1/mch/immunisation/patients/" + baby + "/doses", nurse), dose(org, "PENTA_2", born.plusWeeks(6).plusDays(28)), 201);
        assertThat(status(two, "PENTA_2")).isEqualTo("GIVEN");
        assertThat(two.get("given").asInt()).isEqualTo(2);
    }

    @Test
    void theDueListShowsWhoNeedsWhatAndShrinksAsDosesAreGiven() throws Exception {
        Org org = newOrg("due");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        UUID baby = child(org, "Mwangi", LocalDate.now().minusWeeks(8));
        UUID toddler = child(org, "Njeri", LocalDate.now().minusMonths(30));

        JsonNode all = fetch("/v1/mch/immunisation/due?facilityId=" + org.facilityId() + "&limit=100", nurse).get("data");
        assertThat(count(all, baby, "BCG")).isEqualTo(1);
        assertThat(count(all, baby, "OPV_1")).isEqualTo(1);
        // Ten weeks is not here yet, so PENTA_2 is not due; a 30-month-old owes everything including MR_2.
        assertThat(count(all, baby, "PENTA_2")).isZero();
        assertThat(count(all, toddler, "MR_2")).isEqualTo(1);

        sendJson(post("/v1/mch/immunisation/patients/" + baby + "/doses", nurse), dose(org, "BCG", null), 201);
        JsonNode later = fetch("/v1/mch/immunisation/due?facilityId=" + org.facilityId() + "&limit=100", nurse).get("data");
        assertThat(count(later, baby, "BCG")).isZero();
        // Looking ahead brings in what falls due within the horizon.
        JsonNode ahead = fetch("/v1/mch/immunisation/due?facilityId=" + org.facilityId() + "&horizonDays=21&limit=100", nurse).get("data");
        assertThat(count(ahead, baby, "PENTA_2")).isEqualTo(1);
        // Paging walks the whole list without repeats.
        JsonNode first = fetch("/v1/mch/immunisation/due?facilityId=" + org.facilityId() + "&limit=5", nurse);
        assertThat(first.get("data")).hasSize(5);
        JsonNode second = fetch("/v1/mch/immunisation/due?facilityId=" + org.facilityId() + "&limit=5&cursor=" + first.get("nextCursor").asText(), nurse);
        assertThat(second.get("data").get(0).toString()).isNotEqualTo(first.get("data").get(0).toString());
    }

    @Test
    void accessFollowsThePermissionAndTheOrganisation() throws Exception {
        Org a = newOrg("mch-a");
        Org b = newOrg("mch-b");
        String nurseA = userWithRole(a, "NURSE", "NURSE");
        UUID mother = woman(a, "Private", "1996-04-10");
        String id = sendJson(post("/v1/mch/pregnancies", nurseA), open(a, mother, LocalDate.now().minusDays(60)), 201).get("id").asText();
        // A cashier has no maternal-health access; a pharmacist cannot record a dose; an auditor can read but not write.
        sendJson(post("/v1/mch/pregnancies", userWithRole(a, "CASHIER")), open(a, mother, LocalDate.now().minusDays(60)), 403);
        send(get("/v1/mch/pregnancies/" + id, userWithRole(a, "CASHIER")), 403);
        String auditor = userWithRole(a, "AUDITOR");
        send(get("/v1/mch/pregnancies/" + id, auditor), 200);
        sendJson(post("/v1/mch/pregnancies/" + id + "/visits", auditor), Map.of("systolic", 120, "diastolic", 80), 403);
        // Another organisation cannot see it, even by id.
        send(get("/v1/mch/pregnancies/" + id, b.token()), 404);
        assertThat(fetch("/v1/mch/pregnancies", b.token()).get("data")).isEmpty();
    }

    private static String status(JsonNode card, String vaccine) {
        for (JsonNode d : card.get("doses")) {
            if (d.get("vaccine").asText().equals(vaccine)) {
                return d.get("status").asText();
            }
        }
        throw new AssertionError("no dose " + vaccine);
    }

    private static int count(JsonNode rows, UUID patient, String vaccine) {
        int n = 0;
        for (JsonNode r : rows) {
            if (r.get("patientId").asText().equals(patient.toString()) && r.get("vaccine").asText().equals(vaccine)) {
                n++;
            }
        }
        return n;
    }
}
