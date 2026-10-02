package com.hms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalTest extends IntegrationTest {

    private UUID patient(Org org, String given) throws Exception {
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), given, "Clin" + UUID.randomUUID().toString().substring(0, 6), "1985-03-03")).get("id").asText());
    }

    private String encounter(Org org, String token, UUID patient) throws Exception {
        return create("/v1/clinical/encounters", token, Map.of("facilityId", org.facilityId().toString(), "patientId", patient.toString(), "type", "OPD")).get("id").asText();
    }

    @Test
    void anOutpatientVisitFromTriageToClosure() throws Exception {
        Org org = newOrg("opd");
        String doctor = userWithRole(org, "DOCTOR");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        UUID p = patient(org, "Zawadi");
        String enc = encounter(org, nurse, p);
        sendJson(post("/v1/clinical/encounters/" + enc + "/triage", nurse), Map.of("category", "PRIORITY", "chiefComplaint", "Fever and cough for 3 days"), 200);
        JsonNode v = create("/v1/clinical/encounters/" + enc + "/vitals", nurse, Map.of("tempC", 38.6, "pulse", 104, "systolic", 118, "diastolic", 76, "spo2", 88, "weightKg", 70, "heightCm", 175));
        assertThat(json.convertValue(v.get("alerts"), List.class)).contains("FEVER", "LOW_OXYGEN_SATURATION");
        assertThat(v.get("bmi").asDouble()).isEqualTo(22.9);
        // Implausible and empty readings are refused.
        sendJson(post("/v1/clinical/encounters/" + enc + "/vitals", nurse), Map.of("systolic", 80, "diastolic", 90), 400);
        sendJson(post("/v1/clinical/encounters/" + enc + "/vitals", nurse), Map.of(), 400);
        sendJson(post("/v1/clinical/encounters/" + enc + "/vitals", nurse), Map.of("tempC", 60), 400);

        JsonNode note = create("/v1/clinical/encounters/" + enc + "/notes", doctor, Map.of("kind", "SOAP", "body", "S: fever. O: chest clear. A: URTI. P: fluids."));
        // Without a diagnosis the visit cannot be closed.
        send(post("/v1/clinical/encounters/" + enc + "/close", doctor), 409);
        // ICD-11 shape is enforced; one primary diagnosis per encounter.
        sendJson(post("/v1/clinical/encounters/" + enc + "/diagnoses", doctor), Map.of("icd11Code", "J06.9", "title", "URTI"), 400);
        create("/v1/clinical/encounters/" + enc + "/diagnoses", doctor, Map.of("icd11Code", "CA07", "title", "Acute upper respiratory infection", "kind", "PRIMARY", "certainty", "CONFIRMED"));
        sendJson(post("/v1/clinical/encounters/" + enc + "/diagnoses", doctor), Map.of("icd11Code", "1A00", "title", "Something else", "kind", "PRIMARY"), 409);
        create("/v1/clinical/encounters/" + enc + "/diagnoses", doctor, Map.of("icd11Code", "1A00", "title", "Cholera"));
        // Amending a note keeps the first version.
        JsonNode amended = create("/v1/clinical/notes/" + note.get("threadId").asText() + "/amend", doctor, Map.of("body", "S: fever x3d. O: chest clear. A: URTI. P: fluids, paracetamol.", "reason", "Added plan detail"));
        assertThat(amended.get("version").asInt()).isEqualTo(2);
        assertThat(fetch("/v1/clinical/notes/" + note.get("threadId").asText() + "/history", doctor)).hasSize(2);

        JsonNode detail = fetch("/v1/clinical/encounters/" + enc, doctor);
        assertThat(detail.get("notes")).hasSize(1);
        assertThat(detail.get("notes").get(0).get("version").asInt()).isEqualTo(2);
        assertThat(detail.get("diagnoses")).hasSize(2);
        assertThat(detail.get("encounter").get("triageCategory").asText()).isEqualTo("PRIORITY");

        JsonNode closed = send(post("/v1/clinical/encounters/" + enc + "/close", doctor), 200);
        assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
        // A closed encounter takes no new facts, but a note can still be amended.
        sendJson(post("/v1/clinical/encounters/" + enc + "/vitals", nurse), Map.of("pulse", 80), 409);
        create("/v1/clinical/notes/" + note.get("threadId").asText() + "/amend", doctor, Map.of("body", "Addendum text", "reason", "Late addendum"));
        // The chart open is audited, and the whole chain still verifies.
        assertThat(fetch("/v1/audit/events?entityType=encounter&entityId=" + enc, org.token()).findValuesAsText("action")).contains("encounter.read", "vitals.record", "note.amend", "encounter.close");
        assertThat(fetch("/v1/audit/verify", org.token()).findValuesAsText("intact")).containsOnly("true");
    }

    @Test
    void clinicalFactsCannotBeEditedOrDeletedEvenByTheDatabaseOwner() throws Exception {
        Org org = newOrg("append");
        String doctor = userWithRole(org, "DOCTOR");
        UUID p = patient(org, "Imani");
        String enc = encounter(org, doctor, p);
        JsonNode v = create("/v1/clinical/encounters/" + enc + "/vitals", doctor, Map.of("pulse", 70));
        JsonNode n = create("/v1/clinical/encounters/" + enc + "/notes", doctor, Map.of("kind", "PROGRESS", "body", "stable"));
        assertThrows(Exception.class, () -> asOwner("UPDATE vitals SET pulse = 99 WHERE id = '" + v.get("id").asText() + "'"));
        assertThrows(Exception.class, () -> asOwner("DELETE FROM vitals WHERE id = '" + v.get("id").asText() + "'"));
        assertThrows(Exception.class, () -> asOwner("UPDATE clinical_notes SET body = 'changed' WHERE id = '" + n.get("id").asText() + "'"));
        // A wrong reading is retracted, not removed: it stays visible, marked.
        JsonNode retracted = sendJson(post("/v1/clinical/vitals/" + v.get("id").asText() + "/retract", doctor), Map.of("reason", "wrong patient's reading"), 200);
        assertThat(retracted.get("retracted").asBoolean()).isTrue();
        sendJson(post("/v1/clinical/vitals/" + v.get("id").asText() + "/retract", doctor), Map.of("reason", "again please"), 409);
        assertThat(fetch("/v1/clinical/encounters/" + enc, doctor).get("vitals").get(0).get("retracted").asBoolean()).isTrue();
    }

    @Test
    void aDrugMatchingARecordedAllergyIsStoppedUnlessAClinicianOverridesWithAReason() throws Exception {
        Org org = newOrg("allergy");
        String doctor = userWithRole(org, "DOCTOR");
        UUID p = patient(org, "Baraka");
        String enc = encounter(org, doctor, p);
        create("/v1/clinical/patients/" + p + "/allergies", doctor, Map.of("substance", "Amoxicillin", "reaction", "Anaphylaxis", "severity", "LIFE_THREATENING"));
        Map<String, Object> rx = new java.util.LinkedHashMap<>(Map.of("kind", "MEDICATION", "description", "Amoxicillin 500mg capsules", "drugName", "Amoxicillin 500mg capsule",
                "dose", "500mg", "route", "ORAL", "frequency", "TDS", "durationDays", 5, "quantity", 15));
        JsonNode blocked = sendJson(post("/v1/clinical/encounters/" + enc + "/orders", doctor), rx, 409);
        assertThat(blocked.get("code").asText()).isEqualTo("allergy_conflict");
        // A thin reason is not enough.
        rx.put("allergyOverrideReason", "ok");
        sendJson(post("/v1/clinical/encounters/" + enc + "/orders", doctor), rx, 409);
        rx.put("allergyOverrideReason", "Tolerated previously under observation; benefits outweigh risk");
        JsonNode order = create("/v1/clinical/encounters/" + enc + "/orders", doctor, rx);
        assertThat(json.convertValue(order.get("allergyWarnings"), List.class)).hasSize(1);
        assertThat(fetch("/v1/audit/events?entityType=encounter&entityId=" + enc, org.token()).toString()).contains("allergyOverride");
        // An unrelated drug goes straight through; once the allergy is retired the original passes too.
        create("/v1/clinical/encounters/" + enc + "/orders", doctor, Map.of("kind", "MEDICATION", "description", "Paracetamol", "drugName", "Paracetamol 500mg", "quantity", 20));
        UUID allergyId = UUID.fromString(fetch("/v1/clinical/patients/" + p + "/allergies", doctor).get(0).get("id").asText());
        sendJson(put("/v1/clinical/allergies/" + allergyId + "/status", doctor), Map.of("status", "INACTIVE"), 400);
        sendJson(put("/v1/clinical/allergies/" + allergyId + "/status", doctor), Map.of("status", "INACTIVE", "reason", "Allergy test negative"), 200);
        rx.remove("allergyOverrideReason");
        create("/v1/clinical/encounters/" + enc + "/orders", doctor, rx);
    }

    @Test
    void prescribingIsLimitedToPrescriberCadresAndOrdersCanBeCancelledOnlyBeforeUse() throws Exception {
        Org org = newOrg("rx");
        String doctor = userWithRole(org, "DOCTOR");
        // A nurse who has been given the order permission is still not a prescriber.
        create("/v1/roles", org.token(), Map.of("key", "NURSE_ORDERS", "label", "Nurse with orders", "permissions", List.of("clinical:read", "clinical:write", "orders:write", "patients:read")));
        String nurse = userWithRole(org, "NURSE_ORDERS", "NURSE");
        UUID p = patient(org, "Kamau");
        String enc = encounter(org, doctor, p);
        sendJson(post("/v1/clinical/encounters/" + enc + "/orders", nurse), Map.of("kind", "MEDICATION", "description", "ORS", "drugName", "ORS", "quantity", 5), 403);
        // Non-drug orders by the same nurse are fine.
        JsonNode proc = create("/v1/clinical/encounters/" + enc + "/orders", nurse, Map.of("kind", "PROCEDURE", "description", "Wound dressing"));
        sendJson(post("/v1/clinical/orders/" + proc.get("id").asText() + "/cancel", nurse), Map.of("reason", "not needed"), 200);
        sendJson(post("/v1/clinical/orders/" + proc.get("id").asText() + "/cancel", nurse), Map.of("reason", "again"), 409);
        // A medication order without a quantity is refused.
        sendJson(post("/v1/clinical/encounters/" + enc + "/orders", doctor), Map.of("kind", "MEDICATION", "description", "ORS", "drugName", "ORS"), 400);
    }

    @Test
    void oneOpenEncounterPerKindAndTheAppointmentFollowsTheVisit() throws Exception {
        Org org = newOrg("link");
        UUID p = patient(org, "Njeri");
        String doctor = userWithRole(org, "DOCTOR");
        List<Map<String, Object>> sessions = new java.util.ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            sessions.add(Map.of("practitionerId", org.adminId().toString(), "weekday", d, "startTime", "00:00:00", "endTime", "23:59:00"));
        }
        UUID clinic = UUID.fromString(create("/v1/scheduling/clinics", org.token(), Map.of("facilityId", org.facilityId().toString(), "name", "OPD", "sessions", sessions)).get("id").asText());
        JsonNode walkIn = create("/v1/scheduling/walk-ins", org.token(), Map.of("clinicId", clinic.toString(), "patientId", p.toString()));
        JsonNode enc = create("/v1/clinical/encounters", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "type", "OPD", "appointmentId", walkIn.get("id").asText()));
        assertThat(fetch("/v1/scheduling/appointments/" + walkIn.get("id").asText(), org.token()).get("status").asText()).isEqualTo("IN_PROGRESS");
        sendJson(post("/v1/clinical/encounters", doctor), Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "type", "OPD"), 409);
        // Triage raises the queue priority of the linked appointment.
        sendJson(post("/v1/clinical/encounters/" + enc.get("id").asText() + "/triage", doctor), Map.of("category", "EMERGENCY", "chiefComplaint", "Chest pain"), 200);
        assertThat(fetch("/v1/scheduling/appointments/" + walkIn.get("id").asText(), org.token()).get("priority").asText()).isEqualTo("EMERGENCY");
        sendJson(post("/v1/clinical/encounters/" + enc.get("id").asText() + "/close", doctor), Map.of("noDiagnosisReason", "Left before assessment"), 200);
        assertThat(fetch("/v1/scheduling/appointments/" + walkIn.get("id").asText(), org.token()).get("status").asText()).isEqualTo("COMPLETED");
        // An ED encounter for the same patient is a different kind and is allowed.
        create("/v1/clinical/encounters", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "type", "ED"));
        assertThat(fetch("/v1/clinical/encounters?patientId=" + p + "&limit=1", doctor).get("nextCursor").asText()).isNotBlank();
    }

    @Test
    void chartsAreTenantScopedAndRestrictedPatientsStayClosedWithoutPermission() throws Exception {
        Org a = newOrg("clin-a");
        Org b = newOrg("clin-b");
        String doctorA = userWithRole(a, "DOCTOR");
        UUID p = patient(a, "Secret");
        String enc = encounter(a, doctorA, p);
        send(get("/v1/clinical/encounters/" + enc, b.token()), 404);
        sendJson(post("/v1/clinical/encounters", b.token()), Map.of("facilityId", b.facilityId().toString(), "patientId", p.toString(), "type", "OPD"), 404);
        // The same organisation, but another facility the doctor does not work at.
        JsonNode other = create("/v1/facilities", a.token(), Map.of("name", "Branch"));
        sendJson(post("/v1/clinical/encounters", doctorA), Map.of("facilityId", other.get("id").asText(), "patientId", p.toString(), "type", "OPD"), 403);
        // A restricted patient is invisible to clinicians without the restricted permission.
        asOwner("UPDATE patients SET restricted = true WHERE id = '" + p + "'");
        send(get("/v1/clinical/encounters/" + enc, doctorA), 403);
        send(get("/v1/clinical/patients/" + p + "/allergies", doctorA), 403);
        send(get("/v1/clinical/encounters/" + enc, a.token()), 200);
    }
}
