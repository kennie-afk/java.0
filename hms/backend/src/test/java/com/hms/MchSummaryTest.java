package com.hms;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MchSummaryTest extends IntegrationTest {

    private UUID person(Org org, String family, String birth) throws Exception {
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Mama", family + UUID.randomUUID().toString().substring(0, 5), birth)).get("id").asText());
    }

    @Test
    void theSummaryCountsOnlyWhatHappenedAtThisFacilityInThePeriod() throws Exception {
        Org org = newOrg("sum");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        String f = org.facilityId().toString();

        // Mother one: dangerous reading at her only visit, then a caesarean with a small baby.
        UUID m1 = person(org, "One", "1995-03-03");
        String p1 = sendJson(post("/v1/mch/pregnancies", nurse), Map.of("facilityId", f, "patientId", m1.toString(), "lmp", LocalDate.now().minusDays(200).toString(), "gravida", 2, "parity", 1), 201).get("id").asText();
        sendJson(post("/v1/mch/pregnancies/" + p1 + "/visits", nurse), Map.of("systolic", 170, "diastolic", 112, "hivStatus", "POSITIVE", "iptpGiven", true), 201);
        sendJson(post("/v1/mch/pregnancies/" + p1 + "/delivery", nurse), Map.of("deliveredOn", LocalDate.now().toString(), "mode", "CAESAREAN", "outcome", "LIVE_BIRTH", "birthWeightG", 2100), 200);

        // Mother two: still pregnant, latest visit dangerous and next visit already past.
        UUID m2 = person(org, "Two", "1993-05-05");
        String p2 = sendJson(post("/v1/mch/pregnancies", nurse), Map.of("facilityId", f, "patientId", m2.toString(), "lmp", LocalDate.now().minusDays(120).toString(), "gravida", 1, "parity", 0), 201).get("id").asText();
        Map<String, Object> v = new HashMap<>(Map.of("visitedOn", LocalDate.now().minusDays(10).toString(), "haemoglobin", 6.0, "nextVisitOn", LocalDate.now().minusDays(1).toString(), "ironFolateGiven", true, "hivStatus", "NEGATIVE"));
        sendJson(post("/v1/mch/pregnancies/" + p2 + "/visits", nurse), v, 201);

        // A child with one dose today.
        UUID baby = UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Baby", "Sum" + UUID.randomUUID().toString().substring(0, 5), LocalDate.now().minusDays(3).toString())).get("id").asText());
        sendJson(post("/v1/mch/immunisation/patients/" + baby + "/doses", nurse), Map.of("facilityId", f, "vaccine", "BCG"), 201);

        JsonNode s = fetch("/v1/mch/summary?facilityId=" + f, nurse);
        assertThat(s.at("/pregnancies/active").asInt()).isEqualTo(1);
        assertThat(s.at("/pregnancies/overdueForVisit").asInt()).isEqualTo(1);
        assertThat(s.at("/pregnancies/latestVisitHasDangerSign").asInt()).isEqualTo(1);
        assertThat(s.at("/antenatal/visits").asInt()).isEqualTo(2);
        assertThat(s.at("/antenatal/firstVisits").asInt()).isEqualTo(2);
        assertThat(s.at("/antenatal/iptpGiven").asInt()).isEqualTo(1);
        assertThat(s.at("/antenatal/hivTested").asInt()).isEqualTo(2);
        assertThat(s.at("/antenatal/hivNewPositive").asInt()).isEqualTo(1);
        assertThat(s.at("/deliveries/total").asInt()).isEqualTo(1);
        assertThat(s.at("/deliveries/liveBirths").asInt()).isEqualTo(1);
        assertThat(s.at("/deliveries/lowBirthWeight").asInt()).isEqualTo(1);
        assertThat(s.at("/deliveries/byMode/CAESAREAN").asInt()).isEqualTo(1);
        assertThat(s.at("/immunisation/dosesGiven").asInt()).isEqualTo(1);
        assertThat(s.at("/immunisation/byVaccine/BCG").asInt()).isEqualTo(1);

        // A period that ended before any of it happened counts nothing.
        JsonNode past = fetch("/v1/mch/summary?facilityId=" + f + "&from=2020-01-01&to=2020-01-31", nurse);
        assertThat(past.at("/antenatal/visits").asInt()).isZero();
        assertThat(past.at("/deliveries/total").asInt()).isZero();
        assertThat(past.at("/immunisation/dosesGiven").asInt()).isZero();

        // Backwards and over-long periods are refused; another organisation's facility is not reachable.
        send(get("/v1/mch/summary?facilityId=" + f + "&from=2026-02-01&to=2026-01-01", nurse), 400);
        send(get("/v1/mch/summary?facilityId=" + f + "&from=2020-01-01&to=2026-01-01", nurse), 400);
        Org other = newOrg("sumother");
        String otherNurse = userWithRole(other, "NURSE", "NURSE");
        send(get("/v1/mch/summary?facilityId=" + f, otherNurse), 403);
    }
}
