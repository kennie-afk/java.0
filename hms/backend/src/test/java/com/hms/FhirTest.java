package com.hms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FhirTest extends IntegrationTest {

    private JsonNode fhir(String path, String token, int expected) throws Exception {
        var res = mvc.perform(get("/fhir/r4" + path, token)).andExpect(status().is(expected)).andReturn().getResponse();
        assertThat(res.getContentType()).startsWith("application/fhir+json");
        return json.readTree(res.getContentAsString());
    }

    private JsonNode fhir(String path, String token) throws Exception {
        return fhir(path, token, 200);
    }

    @Test
    void theRecordReadsAsFhirResourcesAndNothingUnvalidatedOrRetractedLeaks() throws Exception {
        Org org = newOrg("fhir");
        String doctor = userWithRole(org, "DOCTOR");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        String tech1 = userWithRole(org, "LAB_TECHNOLOGIST", "LAB_TECHNOLOGIST");
        String tech2 = userWithRole(org, "LAB_TECHNOLOGIST", "LAB_TECHNOLOGIST");
        String client = userWithRole(org, "FHIR_CLIENT", "ADMINISTRATIVE");
        Map<String, Object> reg = patient(org.facilityId(), "Fhir", "Person", "1985-05-05", "phone", "0722123456", "county", "Nakuru");
        reg.put("identifiers", List.of(Map.of("system", "NATIONAL_ID", "value", "29876543")));
        String pid = create("/v1/patients", org.token(), reg).get("id").asText();
        String enc = create("/v1/clinical/encounters", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", pid, "type", "OPD", "chiefComplaint", "Cough")).get("id").asText();
        String vitals = create("/v1/clinical/encounters/" + enc + "/vitals", nurse, Map.of("tempC", 38.6, "pulse", 104, "systolic", 118, "diastolic", 76, "spo2", 94, "weightKg", 70)).get("id").asText();
        String retracted = create("/v1/clinical/encounters/" + enc + "/vitals", nurse, Map.of("pulse", 250)).get("id").asText();
        sendJson(post("/v1/clinical/vitals/" + retracted + "/retract", doctor), Map.of("reason", "wrong patient's reading"), 200);
        create("/v1/clinical/patients/" + pid + "/allergies", doctor, Map.of("substance", "Amoxicillin", "reaction", "Rash", "severity", "SEVERE"));
        create("/v1/clinical/encounters/" + enc + "/orders", doctor, Map.of("kind", "MEDICATION", "description", "Paracetamol", "drugName", "Paracetamol 500 mg", "dose", "1 g", "route", "oral",
                "frequency", "three times daily", "durationDays", 3, "quantity", 18));
        String test = create("/v1/lab/tests", org.token(), Map.of("code", "HB", "name", "Haemoglobin", "loincCode", "718-7", "unit", "g/dL", "refLow", 12, "refHigh", 17)).get("id").asText();
        JsonNode lab = create("/v1/lab/orders", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", pid, "testIds", List.of(test)));
        send(post("/v1/lab/orders/" + lab.get("id").asText() + "/collect", tech1), 200);
        String item = lab.get("items").get(0).get("id").asText();
        sendJson(post("/v1/lab/items/" + item + "/result", tech1), Map.of("numeric", 10.5), 200);

        // Patient
        JsonNode p = fhir("/Patient/" + pid, client);
        assertThat(p.get("resourceType").asText()).isEqualTo("Patient");
        assertThat(p.get("gender").asText()).isEqualTo("female");
        assertThat(p.get("birthDate").asText()).isEqualTo("1985-05-05");
        assertThat(p.get("name").get(0).get("family").asText()).isEqualTo("Person");
        assertThat(p.get("address").get(0).get("state").asText()).isEqualTo("Nakuru");
        assertThat(p.toString()).contains("urn:hms:identifier:NATIONAL_ID").doesNotContain("_created");
        JsonNode found = fhir("/Patient?identifier=urn:hms:identifier:NATIONAL_ID|29876543", client);
        assertThat(found.get("resourceType").asText()).isEqualTo("Bundle");
        assertThat(found.get("type").asText()).isEqualTo("searchset");
        assertThat(found.get("entry")).hasSize(1);
        assertThat(fhir("/Patient?identifier=29876543", client).get("entry")).hasSize(1);
        assertThat(fhir("/Patient?identifier=urn:hms:identifier:PASSPORT|29876543", client).get("entry")).isEmpty();
        assertThat(fhir("/Patient?name=fhir per", client).get("entry")).hasSize(1);
        fhir("/Patient?identifier=http://other.example|1", client, 400);
        // Encounter
        JsonNode e = fhir("/Encounter?patient=Patient/" + pid, client);
        assertThat(e.get("entry")).hasSize(1);
        JsonNode er = e.get("entry").get(0).get("resource");
        assertThat(er.get("status").asText()).isEqualTo("in-progress");
        assertThat(er.get("class").get("code").asText()).isEqualTo("AMB");
        assertThat(er.get("reasonCode").get(0).get("text").asText()).isEqualTo("Cough");
        assertThat(fhir("/Encounter/" + enc, client).get("subject").get("reference").asText()).isEqualTo("Patient/" + pid);
        // Observation: vitals (retracted one absent), blood pressure as a panel with components
        JsonNode vs = fhir("/Observation?patient=" + pid + "&category=vital-signs", client);
        List<String> ids = vs.findValuesAsText("id");
        assertThat(ids).anyMatch(i -> i.equals("vit-" + vitals + "-temp")).noneMatch(i -> i.contains(retracted));
        JsonNode bp = null;
        for (JsonNode en : vs.get("entry")) {
            if (en.get("resource").get("id").asText().endsWith("-bp")) {
                bp = en.get("resource");
            }
        }
        assertThat(bp.get("component")).hasSize(2);
        assertThat(bp.get("code").get("coding").get(0).get("code").asText()).isEqualTo("85354-9");
        assertThat(fhir("/Observation/vit-" + vitals + "-pulse", client).get("valueQuantity").get("value").asInt()).isEqualTo(104);
        fhir("/Observation/vit-" + retracted + "-pulse", client, 404);
        // Observation: lab results appear only once validated
        assertThat(fhir("/Observation?patient=" + pid + "&category=laboratory", client).get("entry")).isEmpty();
        fhir("/Observation/lab-" + item, client, 404);
        send(post("/v1/lab/items/" + item + "/validate", tech2), 200);
        JsonNode labs = fhir("/Observation?patient=" + pid + "&category=laboratory", client);
        assertThat(labs.get("entry")).hasSize(1);
        JsonNode lr = labs.get("entry").get(0).get("resource");
        assertThat(lr.get("code").get("coding").get(0).get("system").asText()).isEqualTo("http://loinc.org");
        assertThat(lr.get("valueQuantity").get("value").decimalValue()).isEqualByComparingTo("10.5");
        assertThat(lr.get("interpretation").get(0).get("coding").get(0).get("code").asText()).isEqualTo("L");
        fhir("/Observation?patient=" + pid, client, 400);
        // MedicationRequest and AllergyIntolerance
        JsonNode mr = fhir("/MedicationRequest?patient=" + pid, client).get("entry").get(0).get("resource");
        assertThat(mr.get("status").asText()).isEqualTo("active");
        assertThat(mr.get("intent").asText()).isEqualTo("order");
        assertThat(mr.get("medicationCodeableConcept").get("text").asText()).isEqualTo("Paracetamol 500 mg");
        assertThat(mr.get("dosageInstruction").get(0).get("text").asText()).contains("1 g", "oral", "three times daily");
        JsonNode al = fhir("/AllergyIntolerance?patient=" + pid, client).get("entry").get(0).get("resource");
        assertThat(al.get("criticality").asText()).isEqualTo("high");
        assertThat(al.get("clinicalStatus").get("coding").get(0).get("code").asText()).isEqualTo("active");
        assertThat(al.get("reaction").get(0).get("severity").asText()).isEqualTo("severe");
        // Every read is on the audit trail.
        assertThat(fetch("/v1/audit/events?entityType=patient&entityId=" + pid, org.token()).findValuesAsText("action")).contains("fhir.read");
        JsonNode meta = fhir("/metadata", client);
        assertThat(meta.get("fhirVersion").asText()).isEqualTo("4.0.1");
    }

    @Test
    void accessIsPermissionGatedRestrictedRecordsNeedAReasonErrorsAreOperationOutcomesAndNothingIsWritable() throws Exception {
        Org org = newOrg("fhirsec");
        String client = userWithRole(org, "FHIR_CLIENT", "ADMINISTRATIVE");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        String pid = create("/v1/patients", org.token(), patient(org.facilityId(), "Secret", "Person", "1970-01-01")).get("id").asText();
        // A clinician without fhir:read is refused, as an OperationOutcome.
        JsonNode denied = fhir("/Patient/" + pid, nurse, 403);
        assertThat(denied.get("resourceType").asText()).isEqualTo("OperationOutcome");
        // Unknown and malformed ids.
        assertThat(fhir("/Patient/" + UUID.randomUUID(), client, 404).get("issue").get(0).get("code").asText()).isEqualTo("not-found");
        fhir("/Patient/not-a-uuid", client, 404);
        // Restricted: absent from search, needs a stated reason on read.
        asOwner("UPDATE patients SET restricted = true WHERE id = '" + pid + "'");
        assertThat(fhir("/Patient?name=secret", org.token()).get("entry")).isEmpty();
        fhir("/Patient/" + pid, org.token(), 403);
        var ok = mvc.perform(get("/fhir/r4/Patient/" + pid, org.token()).header("X-Access-Reason", "Treating in casualty tonight")).andExpect(status().isOk()).andReturn();
        assertThat(ok.getResponse().getContentAsString()).contains("\"Patient\"");
        assertThat(fetch("/v1/audit/events?entityType=patient&entityId=" + pid, org.token()).toString()).contains("casualty");
        // Nothing can be written through this interface.
        mvc.perform(post("/fhir/r4/Patient", client).contentType("application/fhir+json").content("{\"resourceType\":\"Patient\"}")).andExpect(status().is4xxClientError());
        mvc.perform(put("/fhir/r4/Patient/" + pid, client).contentType("application/fhir+json").content("{}")).andExpect(status().is4xxClientError());
        mvc.perform(delete("/fhir/r4/Patient/" + pid, client)).andExpect(status().is4xxClientError());
        // Another organisation sees nothing.
        Org other = newOrg("fhirother");
        String otherClient = userWithRole(other, "FHIR_CLIENT", "ADMINISTRATIVE");
        fhir("/Patient/" + pid, otherClient, 404);
        assertThat(fhir("/Patient", otherClient).get("entry")).isEmpty();
    }

    @Test
    void searchesPageWithANextLinkThatLeadsToTheRest() throws Exception {
        Org org = newOrg("fhirpage");
        String client = userWithRole(org, "FHIR_CLIENT", "ADMINISTRATIVE");
        for (int i = 0; i < 3; i++) {
            create("/v1/patients", org.token(), patient(org.facilityId(), "Page", "Turn" + i + UUID.randomUUID().toString().substring(0, 4), "1990-01-0" + (i + 1)));
        }
        JsonNode first = fhir("/Patient?_count=2", client);
        assertThat(first.get("entry")).hasSize(2);
        String next = null;
        for (JsonNode l : first.get("link")) {
            if (l.get("relation").asText().equals("next")) {
                next = l.get("url").asText();
            }
        }
        assertThat(next).isNotNull();
        JsonNode second = fhir(next.substring(next.indexOf("/Patient")), client);
        assertThat(second.get("entry")).hasSize(1);
        assertThat(second.get("link").findValuesAsText("relation")).doesNotContain("next");
    }
}
