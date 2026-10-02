package com.hms;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LabTest extends IntegrationTest {

    private String test(Org org, String code, String name, Object low, Object high, Object critLow, Object critHigh) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>(Map.of("code", code, "name", name, "unit", "g/dL", "price", 500));
        body.put("refLow", low);
        body.put("refHigh", high);
        body.put("criticalLow", critLow);
        body.put("criticalHigh", critHigh);
        return create("/v1/lab/tests", org.token(), body).get("id").asText();
    }

    private UUID newPatient(Org org) throws Exception {
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Lab", "Pat" + UUID.randomUUID().toString().substring(0, 6), "1988-08-08")).get("id").asText());
    }

    @Test
    void aResultFlowsFromOrderToValidationWithFourEyesAndClinicianVisibilityRules() throws Exception {
        Org org = newOrg("lab");
        String doctor = userWithRole(org, "DOCTOR");
        String tech1 = userWithRole(org, "LAB_TECHNOLOGIST", "LAB_TECHNOLOGIST");
        String tech2 = userWithRole(org, "LAB_TECHNOLOGIST", "LAB_TECHNOLOGIST");
        String hb = test(org, "HB", "Haemoglobin", 12, 17, 7, 20);
        UUID p = newPatient(org);
        JsonNode order = create("/v1/lab/orders", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "priority", "URGENT", "testIds", List.of(hb), "clinicalInfo", "Pallor"));
        String id = order.get("id").asText();
        String item = order.get("items").get(0).get("id").asText();
        assertThat(order.get("orderNumber").asText()).startsWith("LAB-");
        // A result needs a specimen first.
        sendJson(post("/v1/lab/items/" + item + "/result", tech1), Map.of("numeric", 10.5), 409);
        JsonNode collected = send(post("/v1/lab/orders/" + id + "/collect", tech1), 200);
        assertThat(collected.get("status").asText()).isEqualTo("COLLECTED");
        assertThat(collected.get("items").get(0).get("specimenBarcode").asText()).endsWith("-HB");
        // A technologist cannot place orders; entering a text value for a numeric test is refused.
        sendJson(post("/v1/lab/orders", tech1), Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "testIds", List.of(hb)), 403);
        sendJson(post("/v1/lab/items/" + item + "/result", tech1), Map.of("text", "low"), 400);
        JsonNode entered = sendJson(post("/v1/lab/items/" + item + "/result", tech1), Map.of("numeric", 10.5), 200);
        assertThat(entered.get("flag").asText()).isEqualTo("L");
        assertThat(entered.get("critical").asBoolean()).isFalse();
        // The ordering doctor does not see an unvalidated result.
        JsonNode seen = fetch("/v1/lab/orders/" + id, doctor).get("items").get(0);
        assertThat(seen.get("resultHidden").asBoolean()).isTrue();
        assertThat(seen.has("resultNumeric")).isFalse();
        // The same person cannot validate their own entry; a colleague can.
        sendJson(post("/v1/lab/items/" + item + "/validate", tech1), Map.of(), 403);
        send(post("/v1/lab/items/" + item + "/validate", tech2), 200);
        JsonNode visible = fetch("/v1/lab/orders/" + id, doctor);
        assertThat(visible.get("status").asText()).isEqualTo("VALIDATED");
        assertThat(visible.get("items").get(0).get("resultNumeric").decimalValue()).isEqualByComparingTo("10.5");
        // Amending a validated result needs a reason, keeps history, and needs fresh validation by someone else.
        sendJson(post("/v1/lab/items/" + item + "/amend", tech2), Map.of("numeric", 11.2, "reason", "x"), 400);
        JsonNode amended = sendJson(post("/v1/lab/items/" + item + "/amend", tech2), Map.of("numeric", 11.2, "reason", "Transcription error"), 200);
        assertThat(amended.get("status").asText()).isEqualTo("RESULTED");
        assertThat(fetch("/v1/lab/orders/" + id, doctor).get("items").get(0).get("resultHidden").asBoolean()).isTrue();
        sendJson(post("/v1/lab/items/" + item + "/validate", tech2), Map.of(), 403);
        send(post("/v1/lab/items/" + item + "/validate", tech1), 200);
        JsonNode history = fetch("/v1/lab/items/" + item + "/history", tech1);
        assertThat(history).hasSize(2);
        assertThat(history.get(1).get("reason").asText()).isEqualTo("Transcription error");
        // Validated results cannot be cancelled away.
        sendJson(post("/v1/lab/orders/" + id + "/cancel", doctor), Map.of("reason", "changed my mind"), 409);
        assertThat(fetch("/v1/audit/verify", org.token()).findValuesAsText("intact")).containsOnly("true");
    }

    @Test
    void aCriticalValueStaysFlaggedUntilSomeoneAcknowledgesIt() throws Exception {
        Org org = newOrg("critical");
        String doctor = userWithRole(org, "DOCTOR");
        String tech1 = userWithRole(org, "LAB_TECHNOLOGIST", "LAB_TECHNOLOGIST");
        String tech2 = userWithRole(org, "LAB_TECHNOLOGIST", "LAB_TECHNOLOGIST");
        String k = test(org, "K", "Potassium", 3.5, 5.1, 2.5, 6.5);
        UUID p = newPatient(org);
        JsonNode order = create("/v1/lab/orders", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "priority", "STAT", "testIds", List.of(k)));
        String item = order.get("items").get(0).get("id").asText();
        send(post("/v1/lab/orders/" + order.get("id").asText() + "/collect", tech1), 200);
        JsonNode r = sendJson(post("/v1/lab/items/" + item + "/result", tech1), Map.of("numeric", 7.1), 200);
        assertThat(r.get("flag").asText()).isEqualTo("HH");
        assertThat(r.get("critical").asBoolean()).isTrue();
        // Visible to the lab at once; hidden from the doctor until validated.
        assertThat(fetch("/v1/lab/critical?facilityId=" + org.facilityId(), tech1)).hasSize(1);
        assertThat(fetch("/v1/lab/critical?facilityId=" + org.facilityId(), doctor)).isEmpty();
        send(post("/v1/lab/items/" + item + "/validate", tech2), 200);
        JsonNode list = fetch("/v1/lab/critical?facilityId=" + org.facilityId(), doctor);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("testName").asText()).isEqualTo("Potassium");
        assertThat(fetch("/v1/lab/orders?facilityId=" + org.facilityId(), doctor).get("data").get(0).get("hasUnacknowledgedCritical").asBoolean()).isTrue();
        sendJson(post("/v1/lab/items/" + item + "/acknowledge", doctor), Map.of("note", "x"), 400);
        sendJson(post("/v1/lab/items/" + item + "/acknowledge", doctor), Map.of("note", "Patient informed, repeat sent, ECG ordered"), 200);
        sendJson(post("/v1/lab/items/" + item + "/acknowledge", doctor), Map.of("note", "again please ok"), 409);
        assertThat(fetch("/v1/lab/critical?facilityId=" + org.facilityId(), doctor)).isEmpty();
        // The acknowledgement is on the audit trail with its text.
        assertThat(fetch("/v1/audit/events?entityType=patient&entityId=" + p, org.token()).findValuesAsText("action")).contains("lab.critical.ack", "lab.result", "lab.validate");
    }

    @Test
    void theCatalogueRejectsOutOfOrderRangesAndOrdersAreScopedToTheirOrganisation() throws Exception {
        Org a = newOrg("lab-a");
        Org b = newOrg("lab-b");
        sendJson(post("/v1/lab/tests", a.token()), Map.of("code", "BAD", "name", "Bad ranges", "refLow", 10, "refHigh", 5), 400);
        sendJson(post("/v1/lab/tests", a.token()), Map.of("code", "bad", "name", "Lower case code"), 400);
        String hb = test(a, "HB", "Haemoglobin", 12, 17, 7, 20);
        sendJson(post("/v1/lab/tests", a.token()), Map.of("code", "HB", "name", "Haemoglobin again"), 409);
        // Another organisation cannot order our test, or see our orders.
        UUID pb = newPatient(b);
        sendJson(post("/v1/lab/orders", b.token()), Map.of("facilityId", b.facilityId().toString(), "patientId", pb.toString(), "testIds", List.of(hb)), 404);
        UUID pa = newPatient(a);
        JsonNode order = create("/v1/lab/orders", a.token(), Map.of("facilityId", a.facilityId().toString(), "patientId", pa.toString(), "testIds", List.of(hb, hb)));
        assertThat(order.get("items")).hasSize(1);
        send(get("/v1/lab/orders/" + order.get("id").asText(), b.token()), 404);
        assertThat(fetch("/v1/lab/orders", b.token()).get("data")).isEmpty();
        // Cancelling before collection works once.
        sendJson(post("/v1/lab/orders/" + order.get("id").asText() + "/cancel", a.token()), Map.of("reason", "duplicate request"), 200);
        sendJson(post("/v1/lab/orders/" + order.get("id").asText() + "/cancel", a.token()), Map.of("reason", "duplicate request"), 409);
    }
}
