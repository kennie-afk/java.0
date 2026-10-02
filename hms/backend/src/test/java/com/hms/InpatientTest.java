package com.hms;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class InpatientTest extends IntegrationTest {

    private UUID newPatient(Org org, String given) throws Exception {
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), given, "Ward" + UUID.randomUUID().toString().substring(0, 6), "1960-02-02")).get("id").asText());
    }

    private JsonNode ward(Org org, String name, String... beds) throws Exception {
        return create("/v1/inpatient/wards", org.token(), Map.of("facilityId", org.facilityId().toString(), "name", name, "kind", "MEDICAL", "bedLabels", List.of(beds)));
    }

    private String bedId(Org org, String wardId, String label) throws Exception {
        for (JsonNode b : fetch("/v1/inpatient/wards/" + wardId + "/beds", org.token())) {
            if (b.get("label").asText().equals(label)) {
                return b.get("id").asText();
            }
        }
        throw new AssertionError("no bed " + label);
    }

    @Test
    void anAdmissionRunsFromBedToTransferToDischargeAndTheBedsFollow() throws Exception {
        Org org = newOrg("ward");
        String doctor = userWithRole(org, "DOCTOR");
        JsonNode w = ward(org, "Medical Ward", "M1", "M2", "M3");
        String wardId = w.get("id").asText();
        assertThat(w.get("beds").asInt()).isEqualTo(3);
        assertThat(w.get("available").asInt()).isEqualTo(3);
        UUID p = newPatient(org, "Otieno");
        JsonNode adm = sendJson(post("/v1/inpatient/admissions", doctor), Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "bedId", bedId(org, wardId, "M1"),
                "admittingDiagnosis", "Severe pneumonia"), 201);
        String id = adm.get("id").asText();
        assertThat(adm.get("currentBed").asText()).isEqualTo("M1");
        assertThat(adm.get("admissionNumber").asText()).startsWith("ADM-");
        assertThat(fetch("/v1/inpatient/wards?facilityId=" + org.facilityId(), doctor).get(0).get("occupied").asInt()).isEqualTo(1);
        // The bed shows who is in it; the patient has an open inpatient encounter.
        assertThat(fetch("/v1/inpatient/wards/" + wardId + "/beds", doctor).get(0).get("status").asText()).isEqualTo("OCCUPIED");
        assertThat(fetch("/v1/clinical/encounters?patientId=" + p + "&status=OPEN", doctor).get("data").get(0).get("type").asText()).isEqualTo("IPD");
        // Neither the bed nor the patient can be used twice.
        UUID other = newPatient(org, "Akinyi");
        sendJson(post("/v1/inpatient/admissions", doctor), Map.of("facilityId", org.facilityId().toString(), "patientId", other.toString(), "bedId", bedId(org, wardId, "M1")), 409);
        sendJson(post("/v1/inpatient/admissions", doctor), Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "bedId", bedId(org, wardId, "M2")), 409);
        // Transfer to M2: M1 goes to cleaning, history keeps both stays.
        JsonNode moved = sendJson(post("/v1/inpatient/admissions/" + id + "/transfer", doctor), Map.of("toBedId", bedId(org, wardId, "M2"), "reason", "Closer to the nurses station"), 200);
        assertThat(moved.get("currentBed").asText()).isEqualTo("M2");
        assertThat(moved.get("history")).hasSize(2);
        JsonNode beds = fetch("/v1/inpatient/wards/" + wardId + "/beds", doctor);
        assertThat(beds.get(0).get("status").asText()).isEqualTo("CLEANING");
        // A bed in cleaning cannot take a patient until it is released; an occupied bed cannot be flagged.
        sendJson(post("/v1/inpatient/admissions/" + id + "/transfer", doctor), Map.of("toBedId", bedId(org, wardId, "M1"), "reason", "back again"), 409);
        sendJson(put("/v1/inpatient/beds/" + bedId(org, wardId, "M2") + "/status", doctor), Map.of("status", "AVAILABLE"), 409);
        sendJson(put("/v1/inpatient/beds/" + bedId(org, wardId, "M1") + "/status", doctor), Map.of("status", "AVAILABLE"), 204);
        // Discharge needs a primary diagnosis on the inpatient encounter, and a summary.
        String enc = adm.get("encounterId").asText();
        sendJson(post("/v1/inpatient/admissions/" + id + "/discharge", doctor), Map.of("type", "DISCHARGED", "summary", "Recovered on treatment, review in two weeks."), 409);
        sendJson(post("/v1/inpatient/admissions/" + id + "/discharge", doctor), Map.of("type", "DISCHARGED", "summary", "short"), 400);
        create("/v1/clinical/encounters/" + enc + "/diagnoses", doctor, Map.of("icd11Code", "CA40", "title", "Pneumonia", "kind", "PRIMARY", "certainty", "CONFIRMED"));
        JsonNode done = sendJson(post("/v1/inpatient/admissions/" + id + "/discharge", doctor), Map.of("type", "DISCHARGED", "summary", "Recovered on treatment, review in two weeks."), 200);
        assertThat(done.get("status").asText()).isEqualTo("DISCHARGED");
        assertThat(done.has("currentBed")).isFalse();
        assertThat(fetch("/v1/clinical/encounters/" + enc, doctor).get("encounter").get("status").asText()).isEqualTo("CLOSED");
        assertThat(fetch("/v1/inpatient/wards/" + wardId + "/beds", doctor).get(1).get("status").asText()).isEqualTo("CLEANING");
        sendJson(post("/v1/inpatient/admissions/" + id + "/discharge", doctor), Map.of("type", "DISCHARGED", "summary", "Second time is refused."), 409);
        // The patient can be admitted again after discharge.
        sendJson(post("/v1/inpatient/admissions", doctor), Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "bedId", bedId(org, wardId, "M1")), 201);
        assertThat(fetch("/v1/audit/verify", org.token()).findValuesAsText("intact")).containsOnly("true");
    }

    @Test
    void aDeathAtDischargeIsRecordedOnThePatient() throws Exception {
        Org org = newOrg("death");
        String doctor = userWithRole(org, "DOCTOR");
        String wardId = ward(org, "ICU", "I1").get("id").asText();
        UUID p = newPatient(org, "Mwangi");
        JsonNode adm = sendJson(post("/v1/inpatient/admissions", doctor), Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "bedId", bedId(org, wardId, "I1")), 201);
        // A death without a recorded diagnosis needs its reason stated.
        sendJson(post("/v1/inpatient/admissions/" + adm.get("id").asText() + "/discharge", doctor), Map.of("type", "DIED", "summary", "Arrested on arrival to ward."), 409);
        sendJson(post("/v1/inpatient/admissions/" + adm.get("id").asText() + "/discharge", doctor), Map.of("type", "DIED", "summary", "Arrested on arrival to ward.", "noDiagnosisReason", "Cause under investigation"), 200);
        assertThat(fetch("/v1/patients/" + p, doctor).has("deceasedAt")).isTrue();
        // A deceased patient cannot be admitted again, even into a bed that is free.
        send(put("/v1/inpatient/beds/" + bedId(org, wardId, "I1") + "/status", doctor).content("{\"status\":\"AVAILABLE\"}"), 204);
        assertThat(sendJson(post("/v1/inpatient/admissions", doctor), Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "bedId", bedId(org, wardId, "I1")), 409)
                .get("code").asText()).isEqualTo("patient_deceased");
    }

    @Test
    void racingAdmissionsToOneBedAdmitExactlyOne() throws Exception {
        Org org = newOrg("raceward");
        String doctor = userWithRole(org, "DOCTOR");
        String wardId = ward(org, "Surgical", "S1").get("id").asText();
        String bed = bedId(org, wardId, "S1");
        List<UUID> patients = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            patients.add(newPatient(org, "Racer" + (char) ('A' + i)));
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Integer>> results = new ArrayList<>();
        for (UUID p : patients) {
            results.add(pool.submit(() -> mvc.perform(post("/v1/inpatient/admissions", doctor).content(json.writeValueAsString(Map.of("facilityId", org.facilityId().toString(),
                    "patientId", p.toString(), "bedId", bed)))).andReturn().getResponse().getStatus()));
        }
        int created = 0;
        for (Future<Integer> f : results) {
            if (f.get() == 201) {
                created++;
            }
        }
        pool.shutdown();
        assertThat(created).isEqualTo(1);
        assertThat(fetch("/v1/inpatient/admissions?status=ADMITTED", doctor).get("data")).hasSize(1);
        // The losers left nothing behind: no open inpatient encounters for them.
        long open = 0;
        for (UUID p : patients) {
            open += fetch("/v1/clinical/encounters?patientId=" + p + "&status=OPEN", doctor).get("data").size();
        }
        assertThat(open).isEqualTo(1);
    }

    @Test
    void wardsAreFacilityAndTenantScopedAndSetupNeedsTheFacilityRight() throws Exception {
        Org a = newOrg("ward-a");
        Org b = newOrg("ward-b");
        JsonNode w = ward(a, "Private Wing", "P1");
        send(get("/v1/inpatient/wards/" + w.get("id").asText() + "/beds", b.token()), 404);
        sendJson(post("/v1/inpatient/wards", userWithRole(a, "NURSE", "NURSE")), Map.of("facilityId", a.facilityId().toString(), "name", "Nope", "bedLabels", List.of("N1")), 403);
        sendJson(post("/v1/inpatient/wards", a.token()), Map.of("facilityId", a.facilityId().toString(), "name", "Private Wing", "bedLabels", List.of("P9")), 409);
        sendJson(post("/v1/inpatient/wards", b.token()), Map.of("facilityId", a.facilityId().toString(), "name", "Foreign", "bedLabels", List.of("F1")), 403);
        sendJson(post("/v1/inpatient/wards/" + w.get("id").asText() + "/beds", a.token()), Map.of("labels", List.of("P1")), 409);
        assertThat(sendJson(post("/v1/inpatient/wards/" + w.get("id").asText() + "/beds", a.token()), Map.of("labels", List.of("P2", "P3")), 200).get("beds").asInt()).isEqualTo(3);
    }
}
