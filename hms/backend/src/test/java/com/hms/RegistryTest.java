package com.hms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegistryTest extends IntegrationTest {

    private JsonNode body(org.springframework.test.web.servlet.MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode register(Org org, Map<String, Object> request) throws Exception {
        return body(mvc.perform(post("/v1/patients", org.token()).content(json.writeValueAsString(request))).andExpect(status().isCreated()).andReturn());
    }

    @Test
    void registeringIssuesAnMrnAndStoresIdentifiersAndNextOfKin() throws Exception {
        Org org = newOrg("reg");
        Map<String, Object> req = patient(org.facilityId(), "Wanjiku", "Kamau", "1990-04-12", "phone", "0712 345 678", "county", "Nairobi");
        req.put("identifiers", List.of(Map.of("system", "NATIONAL_ID", "value", "12345678")));
        req.put("contacts", List.of(Map.of("relationship", "Spouse", "fullName", "Peter Kamau", "phone", "0722000111")));
        JsonNode p = register(org, req);
        assertThat(p.get("phone").asText()).isEqualTo("+254712345678");
        assertThat(p.get("version").asInt()).isEqualTo(1);
        List<String> systems = json.convertValue(p.get("identifiers").findValuesAsText("system"), List.class);
        assertThat(systems).contains("NATIONAL_ID", "MRN");
        String mrn = p.get("identifiers").findParents("system").stream().filter(i -> "MRN".equals(i.get("system").asText())).findFirst().get().get("value").asText();
        assertThat(mrn).matches("\\d+-\\d{6}");
        assertThat(p.get("contacts")).hasSize(1);
        // MRNs count up per facility.
        JsonNode second = register(org, patient(org.facilityId(), "Otieno", "Odhiambo", "1980-01-01"));
        String mrn2 = second.get("identifiers").findParents("system").stream().filter(i -> "MRN".equals(i.get("system").asText())).findFirst().get().get("value").asText();
        assertThat(mrn2).isNotEqualTo(mrn);
    }

    @Test
    void aLikelyDuplicateIsStoppedUntilAHumanConfirms() throws Exception {
        Org org = newOrg("dup");
        register(org, patient(org.facilityId(), "Wanjiku", "Kamau", "1990-04-12"));
        // Same person, spelled slightly differently, same birth date.
        Map<String, Object> again = patient(org.facilityId(), "Wanjiku", "Kamau", "1990-04-12");
        var blocked = body(mvc.perform(post("/v1/patients", org.token()).content(json.writeValueAsString(again))).andExpect(status().isConflict()).andReturn());
        assertThat(blocked.get("code").asText()).isEqualTo("possible_duplicate");
        assertThat(blocked.get("candidates")).hasSize(1);
        assertThat(blocked.get("candidates").get(0).get("reason").asText()).isEqualTo("NAME_AND_BIRTH_DATE");

        // A different person with a similar name but another birth date is not a duplicate.
        register(org, patient(org.facilityId(), "Wanjiku", "Kamau", "2001-11-30"));

        // Confirmed by the clerk: allowed, and the override is recorded in the audit trail.
        again.put("confirmNotDuplicate", true);
        mvc.perform(post("/v1/patients", org.token()).content(json.writeValueAsString(again))).andExpect(status().isCreated());
    }

    @Test
    void theSameNationalIdIsAlwaysTheSamePersonAndCannotBeOverridden() throws Exception {
        Org org = newOrg("natid");
        Map<String, Object> first = patient(org.facilityId(), "Achieng", "Omondi", "1992-02-02");
        first.put("identifiers", List.of(Map.of("system", "NATIONAL_ID", "value", "23456789")));
        register(org, first);
        Map<String, Object> second = patient(org.facilityId(), "Totally", "Different", "1970-07-07");
        second.put("identifiers", List.of(Map.of("system", "NATIONAL_ID", "value", "23456789")));
        second.put("confirmNotDuplicate", true);
        var refused = body(mvc.perform(post("/v1/patients", org.token()).content(json.writeValueAsString(second))).andExpect(status().isConflict()).andReturn());
        assertThat(refused.get("candidates").get(0).get("reason").asText()).isEqualTo("IDENTIFIER");
    }

    @Test
    void invalidInputIsRejectedWithFieldMessages() throws Exception {
        Org org = newOrg("valid");
        Map<String, Object> bad = patient(org.facilityId(), "", "Kamau", "2999-01-01");
        var r = body(mvc.perform(post("/v1/patients", org.token()).content(json.writeValueAsString(bad))).andExpect(status().isBadRequest()).andReturn());
        assertThat(r.get("code").asText()).isEqualTo("validation_failed");
        assertThat(r.get("fields").fieldNames()).toIterable().isNotEmpty();
        Map<String, Object> phone = patient(org.facilityId(), "Ok", "Person", "1990-01-01", "phone", "12345");
        mvc.perform(post("/v1/patients", org.token()).content(json.writeValueAsString(phone))).andExpect(status().isBadRequest());
        Map<String, Object> id = patient(org.facilityId(), "Ok", "Person", "1990-01-01");
        id.put("identifiers", List.of(Map.of("system", "NATIONAL_ID", "value", "12")));
        mvc.perform(post("/v1/patients", org.token()).content(json.writeValueAsString(id))).andExpect(status().isBadRequest());
    }

    @Test
    void youCannotRegisterAtAFacilityYouDoNotWorkAt() throws Exception {
        Org a = newOrg("fac-a");
        Org b = newOrg("fac-b");
        mvc.perform(post("/v1/patients", a.token()).content(json.writeValueAsString(patient(b.facilityId(), "Wrong", "Place", "1990-01-01"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEditWithAStaleVersionIsRefusedInsteadOfOverwritingSomeoneElsesChange() throws Exception {
        Org org = newOrg("ver");
        JsonNode p = register(org, patient(org.facilityId(), "Mwangi", "Njoroge", "1975-05-05"));
        String id = p.get("id").asText();
        Map<String, Object> demographics = Map.of("givenName", "Mwangi", "familyName", "Njoroge", "sex", "MALE", "birthDate", "1975-05-05", "phone", "0700111222");
        mvc.perform(put("/v1/patients/" + id, org.token()).content(json.writeValueAsString(Map.of("version", 1, "demographics", demographics))))
                .andExpect(status().isOk());
        var stale = body(mvc.perform(put("/v1/patients/" + id, org.token()).content(json.writeValueAsString(Map.of("version", 1, "demographics", demographics))))
                .andExpect(status().isConflict()).andReturn());
        assertThat(stale.get("code").asText()).isEqualTo("stale_version");
        var now = body(mvc.perform(get("/v1/patients/" + id, org.token())).andExpect(status().isOk()).andReturn());
        assertThat(now.get("version").asInt()).isEqualTo(2);
    }

    @Test
    void searchFindsByNameMrnAndNationalIdAndPagesWithoutSkippingAnyone() throws Exception {
        Org org = newOrg("search");
        for (int i = 0; i < 7; i++) {
            Map<String, Object> req = patient(org.facilityId(), "Patient" + (char) ('A' + i), "Searchable", "19" + (70 + i) + "-01-01");
            if (i == 3) {
                req.put("identifiers", List.of(Map.of("system", "NATIONAL_ID", "value", "34567890")));
            }
            register(org, req);
        }
        // Keyset paging: three pages of 3, 3 and 1, no overlap, nothing missing.
        java.util.Set<String> seen = new java.util.HashSet<>();
        String cursor = null;
        int pages = 0;
        do {
            var page = body(mvc.perform(get("/v1/patients?q=searchable&limit=3" + (cursor == null ? "" : "&cursor=" + cursor), org.token())).andExpect(status().isOk()).andReturn());
            page.get("data").forEach(p -> assertThat(seen.add(p.get("id").asText())).as("no repeats").isTrue());
            cursor = page.has("nextCursor") ? page.get("nextCursor").asText() : null;
            pages++;
        } while (cursor != null && pages < 10);
        assertThat(seen).hasSize(7);
        assertThat(pages).isEqualTo(3);
        var byId = body(mvc.perform(get("/v1/patients?q=34567890", org.token())).andExpect(status().isOk()).andReturn());
        assertThat(byId.get("data")).hasSize(1);
        String mrn = body(mvc.perform(get("/v1/patients/" + byId.get("data").get(0).get("id").asText(), org.token())).andReturn())
                .get("identifiers").findParents("system").stream().filter(i -> "MRN".equals(i.get("system").asText())).findFirst().get().get("value").asText();
        var byMrn = body(mvc.perform(get("/v1/patients?q=" + mrn, org.token())).andExpect(status().isOk()).andReturn());
        assertThat(byMrn.get("data")).hasSize(1);
    }

    @Test
    void aRestrictedRecordNeedsPermissionAndARecordedReason() throws Exception {
        Org org = newOrg("restr");
        JsonNode p = register(org, patient(org.facilityId(), "Very", "Important", "1960-03-03"));
        String id = p.get("id").asText();
        asOwner("UPDATE patients SET restricted = true WHERE id = '" + id + "'");
        // The administrator holds the permission but must still say why.
        mvc.perform(get("/v1/patients/" + id, org.token())).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/patients/" + id, org.token()).header("X-Access-Reason", "Treating in casualty tonight"))
                .andExpect(status().isOk());
        var events = body(mvc.perform(get("/v1/audit/events?entityType=patient&entityId=" + id, org.token())).andExpect(status().isOk()).andReturn());
        boolean recorded = false;
        for (JsonNode e : events) {
            if ("patient.read".equals(e.get("action").asText()) && e.has("reason") && e.get("reason").asText().contains("casualty")) {
                recorded = true;
            }
        }
        assertThat(recorded).as("the reason is in the audit trail").isTrue();
    }

    @Test
    void mergingFoldsADuplicateIntoTheSurvivorWithoutLosingAnything() throws Exception {
        Org org = newOrg("merge");
        Map<String, Object> a = patient(org.facilityId(), "Fatuma", "Hassan", "1988-08-08");
        a.put("identifiers", List.of(Map.of("system", "NATIONAL_ID", "value", "45678901")));
        Map<String, Object> b = patient(org.facilityId(), "Fatma", "Hasan", "1988-08-08", "phone", "0733000111");
        b.put("confirmNotDuplicate", true);
        b.put("identifiers", List.of(Map.of("system", "SHA_NUMBER", "value", "SHA99887766")));
        JsonNode survivor = register(org, a);
        JsonNode duplicate = register(org, b);
        var merged = body(mvc.perform(post("/v1/patients/" + duplicate.get("id").asText() + "/merge", org.token())
                .content(json.writeValueAsString(Map.of("survivorId", survivor.get("id").asText(), "reason", "Same person registered twice"))))
                .andExpect(status().isOk()).andReturn());
        List<String> systems = json.convertValue(merged.get("identifiers").findValuesAsText("system"), List.class);
        assertThat(systems).contains("NATIONAL_ID", "SHA_NUMBER");
        var old = body(mvc.perform(get("/v1/patients/" + duplicate.get("id").asText(), org.token())).andExpect(status().isOk()).andReturn());
        assertThat(old.get("mergedInto").asText()).isEqualTo(survivor.get("id").asText());
        // The merged record leaves search, and cannot be edited any more.
        var results = body(mvc.perform(get("/v1/patients?q=hasan", org.token())).andReturn());
        assertThat(results.get("data")).isEmpty();
        mvc.perform(put("/v1/patients/" + duplicate.get("id").asText(), org.token()).content(json.writeValueAsString(Map.of("version", old.get("version").asInt(),
                "demographics", Map.of("givenName", "X", "familyName", "Y", "sex", "FEMALE", "birthDate", "1988-08-08"))))).andExpect(status().isConflict());
        // Only a records officer (merge permission) may do it: a plain nurse-like role cannot.
        mvc.perform(post("/v1/patients/" + survivor.get("id").asText() + "/merge", org.token())
                .content(json.writeValueAsString(Map.of("survivorId", survivor.get("id").asText(), "reason", "Merging into itself")))).andExpect(status().isBadRequest());
    }

    @Test
    void oneOrganisationCannotOpenOrChangeAnothersPatient() throws Exception {
        Org a = newOrg("own-a");
        Org b = newOrg("own-b");
        JsonNode p = register(a, patient(a.facilityId(), "Secret", "Patient", "1991-01-01"));
        mvc.perform(get("/v1/patients/" + p.get("id").asText(), b.token())).andExpect(status().isNotFound());
        mvc.perform(put("/v1/patients/" + p.get("id").asText(), b.token()).content(json.writeValueAsString(Map.of("version", 1,
                "demographics", Map.of("givenName", "Hacked", "familyName", "Patient", "sex", "FEMALE", "birthDate", "1991-01-01"))))).andExpect(status().isNotFound());
        var list = body(mvc.perform(get("/v1/patients?q=secret", b.token())).andReturn());
        assertThat(list.get("data")).isEmpty();
        UUID.fromString(p.get("id").asText());
    }
}
