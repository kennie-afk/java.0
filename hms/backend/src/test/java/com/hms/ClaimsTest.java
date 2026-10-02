package com.hms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimsTest extends IntegrationTest {

    private record Visit(UUID patient, String encounter, String invoice, String doctor) {}

    /** A finished outpatient visit with an issued invoice, built through the real API. */
    private Visit visit(Org org, String doctor, boolean withSha, boolean close, String payer) throws Exception {
        Map<String, Object> reg = patient(org.facilityId(), "Claim", "Pat" + UUID.randomUUID().toString().substring(0, 6), "1975-06-06");
        List<Map<String, String>> ids = new java.util.ArrayList<>();
        ids.add(Map.of("system", "NATIONAL_ID", "value", String.valueOf(10000000 + (int) (Math.random() * 80000000))));
        if (withSha) {
            ids.add(Map.of("system", "SHA_NUMBER", "value", "SHA" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
        }
        reg.put("identifiers", ids);
        UUID p = UUID.fromString(create("/v1/patients", org.token(), reg).get("id").asText());
        String enc = create("/v1/clinical/encounters", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "type", "OPD")).get("id").asText();
        create("/v1/clinical/encounters/" + enc + "/diagnoses", doctor, Map.of("icd11Code", "1A00", "title", "Cholera", "kind", "PRIMARY", "certainty", "CONFIRMED"));
        if (close) {
            send(post("/v1/clinical/encounters/" + enc + "/close", doctor), 200);
        }
        String inv = create("/v1/billing/invoices", org.token(), Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "encounterId", enc, "payerType", payer)).get("id").asText();
        sendJson(post("/v1/billing/invoices/" + inv + "/lines", org.token()), Map.of("description", "Consultation", "unitPrice", 1500, "quantity", 1), 200);
        send(post("/v1/billing/invoices/" + inv + "/issue", org.token()), 200);
        return new Visit(p, enc, inv, doctor);
    }

    private List<String> rules(JsonNode claim) {
        return claim.get("issues").findValuesAsText("ruleCode");
    }

    @Test
    void aCompleteClaimIsReadyAndSubmissionToTheStubIsHonestAboutSendingNothing() throws Exception {
        Org org = newOrg("claim");
        String doctor = userWithRole(org, "DOCTOR");
        String officer = userWithRole(org, "CLAIMS_OFFICER", "ADMINISTRATIVE");
        Visit v = visit(org, doctor, true, true, "SHA");
        JsonNode claim = create("/v1/claims", officer, Map.of("invoiceId", v.invoice()));
        assertThat(claim.get("status").asText()).isEqualTo("READY");
        assertThat(claim.get("claimNumber").asText()).startsWith("CLM-");
        assertThat(claim.get("disclaimer").asText()).contains("not the SHA/DHA rule set");
        assertThat(claim.get("bundle").get("diagnoses")).hasSize(1);
        assertThat(claim.get("bundle").get("notFor").asText()).contains("Not a DHA eClaims FHIR resource");
        String id = claim.get("id").asText();
        JsonNode submitted = send(post("/v1/claims/" + id + "/submit", officer), 200);
        assertThat(submitted.get("status").asText()).isEqualTo("SUBMISSION_STUBBED");
        JsonNode sub = submitted.get("submissions").get(0);
        assertThat(sub.get("adapter").asText()).isEqualTo("unverified-stub");
        assertThat(sub.get("verified").asBoolean()).isFalse();
        assertThat(sub.get("sent").asBoolean()).isFalse();
        assertThat(sub.get("detail").asText()).contains("nothing was transmitted");
        // Not resubmittable, and the submission log cannot be rewritten.
        send(post("/v1/claims/" + id + "/submit", officer), 409);
        assertThrows(Exception.class, () -> asOwner("DELETE FROM claim_submissions WHERE claim_id = '" + id + "'"));
        assertThat(fetch("/v1/audit/events?entityType=claim&entityId=" + id, org.token()).findValuesAsText("action")).contains("claim.assemble", "claim.submit");
        assertThat(fetch("/v1/audit/verify", org.token()).findValuesAsText("intact")).containsOnly("true");
    }

    @Test
    void missingPiecesAreNamedAndFixingThemMakesTheClaimReady() throws Exception {
        Org org = newOrg("readiness");
        String doctor = userWithRole(org, "DOCTOR");
        String officer = userWithRole(org, "CLAIMS_OFFICER", "ADMINISTRATIVE");
        Visit v = visit(org, doctor, false, false, "SHA");
        JsonNode claim = create("/v1/claims", officer, Map.of("invoiceId", v.invoice()));
        assertThat(claim.get("status").asText()).isEqualTo("NEEDS_ATTENTION");
        assertThat(rules(claim)).contains("PATIENT_SHA_NUMBER_MISSING", "ENCOUNTER_OPEN");
        // Cannot be submitted while it has errors.
        String id = claim.get("id").asText();
        send(post("/v1/claims/" + id + "/submit", officer), 409);
        // Fix: add the SHA number, close the encounter.
        sendJson(post("/v1/patients/" + v.patient() + "/identifiers", org.token()), Map.of("system", "SHA_NUMBER", "value", "SHA12345678"), 201);
        send(post("/v1/clinical/encounters/" + v.encounter() + "/close", doctor), 200);
        JsonNode fixed = send(post("/v1/claims/" + id + "/reassemble", officer), 200);
        assertThat(fixed.get("status").asText()).isEqualTo("READY");
        assertThat(rules(fixed)).doesNotContain("PATIENT_SHA_NUMBER_MISSING", "ENCOUNTER_OPEN");
        // The work list tells the office what is failing across claims.
        JsonNode summary = fetch("/v1/claims/summary?facilityId=" + org.facilityId(), officer);
        assertThat(summary.get("byStatus").get("READY").asInt()).isEqualTo(1);
    }

    @Test
    void aClaimBuiltFromChangedRecordsIsRebuiltInsteadOfSubmittedStale() throws Exception {
        Org org = newOrg("stale");
        String doctor = userWithRole(org, "DOCTOR");
        String officer = userWithRole(org, "CLAIMS_OFFICER", "ADMINISTRATIVE");
        Visit v = visit(org, doctor, true, true, "SHA");
        String id = create("/v1/claims", officer, Map.of("invoiceId", v.invoice())).get("id").asText();
        // The patient's details are corrected after assembly.
        JsonNode p = fetch("/v1/patients/" + v.patient(), org.token());
        Map<String, Object> d = new java.util.LinkedHashMap<>(Map.of("givenName", "Corrected", "familyName", p.get("familyName").asText(), "sex", p.get("sex").asText(), "birthDate", p.get("birthDate").asText()));
        sendJson(put("/v1/patients/" + v.patient(), org.token()), Map.of("version", p.get("version").asInt(), "demographics", d), 200);
        sendJson(post("/v1/claims/" + id + "/submit", officer), Map.of(), 409);
        JsonNode rebuilt = fetch("/v1/claims/" + id, officer);
        assertThat(rebuilt.get("status").asText()).isEqualTo("READY");
        assertThat(rebuilt.get("bundle").get("patient").get("givenName").asText()).isEqualTo("Corrected");
        assertThat(rebuilt.get("submissions")).isEmpty();
        send(post("/v1/claims/" + id + "/submit", officer), 200);
    }

    @Test
    void oneLiveClaimPerInvoiceWithdrawalFreesItAndCashInvoicesAreRefused() throws Exception {
        Org org = newOrg("onelive");
        String doctor = userWithRole(org, "DOCTOR");
        String officer = userWithRole(org, "CLAIMS_OFFICER", "ADMINISTRATIVE");
        Visit v = visit(org, doctor, true, true, "SHA");
        String id = create("/v1/claims", officer, Map.of("invoiceId", v.invoice())).get("id").asText();
        sendJson(post("/v1/claims", officer), Map.of("invoiceId", v.invoice()), 409);
        sendJson(post("/v1/claims/" + id + "/withdraw", officer), Map.of("reason", "x"), 400);
        sendJson(post("/v1/claims/" + id + "/withdraw", officer), Map.of("reason", "Wrong visit attached"), 200);
        send(post("/v1/claims/" + id + "/reassemble", officer), 409);
        create("/v1/claims", officer, Map.of("invoiceId", v.invoice()));
        Visit cash = visit(org, doctor, true, true, "CASH");
        sendJson(post("/v1/claims", officer), Map.of("invoiceId", cash.invoice()), 400);
        assertThat(fetch("/v1/claims?facilityId=" + org.facilityId() + "&status=WITHDRAWN", officer).get("data")).hasSize(1);
        // A nurse has no claims rights at all.
        sendJson(post("/v1/claims", userWithRole(org, "NURSE", "NURSE")), Map.of("invoiceId", v.invoice()), 403);
    }

    @Test
    void claimsBelongToOneOrganisation() throws Exception {
        Org a = newOrg("claim-a");
        Org b = newOrg("claim-b");
        String doctor = userWithRole(a, "DOCTOR");
        Visit v = visit(a, doctor, true, true, "SHA");
        String id = create("/v1/claims", a.token(), Map.of("invoiceId", v.invoice())).get("id").asText();
        send(get("/v1/claims/" + id, b.token()), 404);
        sendJson(post("/v1/claims", b.token()), Map.of("invoiceId", v.invoice()), 404);
        assertThat(fetch("/v1/claims", b.token()).get("data")).isEmpty();
    }
}
