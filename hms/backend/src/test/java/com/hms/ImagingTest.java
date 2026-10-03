package com.hms;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImagingTest extends IntegrationTest {

    private String procedure(Org org, String code, String name, String modality) throws Exception {
        return create("/v1/imaging/procedures", org.token(), Map.of("code", code, "name", name, "modality", modality, "price", 1500)).get("id").asText();
    }

    private UUID newPatient(Org org) throws Exception {
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Img", "Pat" + UUID.randomUUID().toString().substring(0, 6), "1975-05-05")).get("id").asText());
    }

    private Map<String, Object> order(Org org, UUID patient, String procedure) {
        return Map.of("facilityId", org.facilityId().toString(), "patientId", patient.toString(), "procedureId", procedure, "priority", "URGENT", "clinicalInfo", "Cough, fever");
    }

    @Test
    void aStudyFlowsFromOrderToSignedReportWithFourEyesAndClinicianVisibilityRules() throws Exception {
        Org org = newOrg("img");
        String doctor = userWithRole(org, "DOCTOR");
        String radiographer = userWithRole(org, "RADIOGRAPHER", "RADIOGRAPHER");
        String radiologist = userWithRole(org, "RADIOLOGIST");
        String radiologist2 = userWithRole(org, "RADIOLOGIST");
        String cxr = procedure(org, "CXR", "Chest X-ray, PA", "XR");
        UUID p = newPatient(org);
        JsonNode o = create("/v1/imaging/orders", doctor, order(org, p, cxr));
        String id = o.get("id").asText();
        assertThat(o.get("orderNumber").asText()).startsWith("IMG-");
        // A report needs the study performed first; radiographers cannot place orders.
        sendJson(post("/v1/imaging/orders/" + id + "/report", radiographer), Map.of("findings", "Clear lungs", "impression", "Normal study"), 409);
        sendJson(post("/v1/imaging/orders", radiographer), order(org, p, cxr), 403);
        assertThat(sendJson(post("/v1/imaging/orders/" + id + "/perform", radiographer), Map.of("techniqueNote", "PA erect"), 200).get("status").asText()).isEqualTo("PERFORMED");
        sendJson(post("/v1/imaging/orders/" + id + "/report", radiographer), Map.of("findings", "Clear lungs", "impression", ""), 400);
        JsonNode reported = sendJson(post("/v1/imaging/orders/" + id + "/report", radiographer), Map.of("findings", "Clear lungs", "impression", "Normal study"), 200);
        assertThat(reported.get("status").asText()).isEqualTo("REPORTED");
        // The ordering doctor does not see the unsigned report.
        JsonNode seen = fetch("/v1/imaging/orders/" + id, doctor);
        assertThat(seen.get("reportHidden").asBoolean()).isTrue();
        assertThat(seen.has("impression")).isFalse();
        // Writer cannot sign (and a radiographer lacks the right at all); a different radiologist can.
        sendJson(post("/v1/imaging/orders/" + id + "/sign", radiographer), Map.of(), 403);
        send(post("/v1/imaging/orders/" + id + "/sign", radiologist), 200);
        JsonNode visible = fetch("/v1/imaging/orders/" + id, doctor);
        assertThat(visible.get("status").asText()).isEqualTo("SIGNED");
        assertThat(visible.get("impression").asText()).isEqualTo("Normal study");
        // Amendment needs a reason, is kept in history, and needs a fresh signature by someone other than the amender.
        sendJson(post("/v1/imaging/orders/" + id + "/amend", radiologist), Map.of("impression", "Small effusion", "reason", "x"), 400);
        assertThat(sendJson(post("/v1/imaging/orders/" + id + "/amend", radiologist), Map.of("impression", "Small right effusion", "reason", "Missed on first read"), 200).get("status").asText()).isEqualTo("REPORTED");
        sendJson(post("/v1/imaging/orders/" + id + "/sign", radiologist), Map.of(), 403);
        send(post("/v1/imaging/orders/" + id + "/sign", radiologist2), 200);
        JsonNode history = fetch("/v1/imaging/orders/" + id + "/history", radiologist);
        assertThat(history).hasSize(2);
        assertThat(history.get(1).get("reason").asText()).isEqualTo("Missed on first read");
        // A signed report cannot be cancelled away.
        sendJson(post("/v1/imaging/orders/" + id + "/cancel", doctor), Map.of("reason", "changed my mind"), 409);
        assertThat(fetch("/v1/audit/verify", org.token()).findValuesAsText("intact")).containsOnly("true");
    }

    @Test
    void aCriticalFindingStaysFlaggedUntilSomeoneAcknowledgesIt() throws Exception {
        Org org = newOrg("imgcrit");
        String doctor = userWithRole(org, "DOCTOR");
        String writer = userWithRole(org, "RADIOLOGIST");
        String signer = userWithRole(org, "RADIOLOGIST");
        String ct = procedure(org, "CTH", "CT head, plain", "CT");
        UUID p = newPatient(org);
        String id = create("/v1/imaging/orders", doctor, order(org, p, ct)).get("id").asText();
        send(post("/v1/imaging/orders/" + id + "/perform", writer), 200);
        // Critical needs a note.
        sendJson(post("/v1/imaging/orders/" + id + "/report", writer), Map.of("impression", "Acute bleed", "critical", true), 400);
        sendJson(post("/v1/imaging/orders/" + id + "/report", writer), Map.of("findings", "Hyperdense collection", "impression", "Acute subdural haemorrhage", "critical", true, "criticalNote", "Acute subdural, mass effect"), 200);
        assertThat(fetch("/v1/imaging/critical?facilityId=" + org.facilityId(), writer)).hasSize(1);
        assertThat(fetch("/v1/imaging/critical?facilityId=" + org.facilityId(), doctor)).isEmpty();
        send(post("/v1/imaging/orders/" + id + "/sign", signer), 200);
        JsonNode list = fetch("/v1/imaging/critical?facilityId=" + org.facilityId(), doctor);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("procedureName").asText()).isEqualTo("CT head, plain");
        assertThat(fetch("/v1/imaging/orders?facilityId=" + org.facilityId(), doctor).get("data").get(0).get("hasUnacknowledgedCritical").asBoolean()).isTrue();
        sendJson(post("/v1/imaging/orders/" + id + "/acknowledge", doctor), Map.of("note", "x"), 400);
        sendJson(post("/v1/imaging/orders/" + id + "/acknowledge", doctor), Map.of("note", "Neurosurgery called, patient transferred"), 200);
        sendJson(post("/v1/imaging/orders/" + id + "/acknowledge", doctor), Map.of("note", "again please ok"), 409);
        assertThat(fetch("/v1/imaging/critical?facilityId=" + org.facilityId(), doctor)).isEmpty();
        assertThat(fetch("/v1/audit/events?entityType=patient&entityId=" + p, org.token()).findValuesAsText("action")).contains("imaging.critical.ack", "imaging.report", "imaging.sign");
    }

    @Test
    void theCatalogueAndOrdersAreScopedToTheirOrganisationAndInactiveProceduresAreRefused() throws Exception {
        Org a = newOrg("img-a");
        Org b = newOrg("img-b");
        String us = procedure(a, "USA", "Ultrasound abdomen", "US");
        sendJson(post("/v1/imaging/procedures", a.token()), Map.of("code", "USA", "name", "Duplicate", "modality", "US"), 409);
        sendJson(post("/v1/imaging/procedures", a.token()), Map.of("code", "bad", "name", "Lower case", "modality", "US"), 400);
        sendJson(post("/v1/imaging/procedures", a.token()), Map.of("code", "ODD", "name", "Odd modality", "modality", "ZZ"), 400);
        UUID pb = newPatient(b);
        sendJson(post("/v1/imaging/orders", b.token()), order(b, pb, us), 404);
        UUID pa = newPatient(a);
        String id = create("/v1/imaging/orders", a.token(), order(a, pa, us)).get("id").asText();
        send(get("/v1/imaging/orders/" + id, b.token()), 404);
        assertThat(fetch("/v1/imaging/orders", b.token()).get("data")).isEmpty();
        sendJson(post("/v1/imaging/orders/" + id + "/cancel", a.token()), Map.of("reason", "duplicate request"), 200);
        sendJson(post("/v1/imaging/orders/" + id + "/cancel", a.token()), Map.of("reason", "duplicate request"), 409);
        sendJson(put("/v1/imaging/procedures/" + us, a.token()), Map.of("code", "USA", "name", "Ultrasound abdomen", "modality", "US", "active", false), 200);
        sendJson(post("/v1/imaging/orders", a.token()), order(a, pa, us), 409);
    }
}
