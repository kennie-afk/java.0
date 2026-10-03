package com.hms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortalTest extends IntegrationTest {

    private static String slug(Org org) {
        return org.email().replace("@example.org", "");
    }

    private UUID newPatient(Org org, String given, String birth) throws Exception {
        return UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), given, "Portal" + UUID.randomUUID().toString().substring(0, 5), birth, "phone", "07" + (int) (Math.random() * 90000000 + 10000000))).get("id").asText());
    }

    private String activate(Org org, UUID patient, String login, String password) throws Exception {
        String code = create("/v1/portal/invitations", org.token(), Map.of("patientId", patient.toString())).get("code").asText();
        sendJson(post("/portal/auth/activate", null), Map.of("organisation", slug(org), "code", code, "birthDate", "1990-04-04", "login", login, "password", password), 200);
        return portalLogin(org, login, password);
    }

    private String portalLogin(Org org, String login, String password) throws Exception {
        return sendJson(post("/portal/auth/login", null), Map.of("organisation", slug(org), "login", login, "password", password), 200).get("token").asText();
    }

    private JsonNode portal(String path, String token) throws Exception {
        return fetch("/portal" + path, token);
    }

    private String validatedLab(Org org, String doctor, String tech1, String tech2, UUID patient, String test) throws Exception {
        JsonNode lab = create("/v1/lab/orders", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", patient.toString(), "testIds", List.of(test)));
        send(post("/v1/lab/orders/" + lab.get("id").asText() + "/collect", tech1), 200);
        String item = lab.get("items").get(0).get("id").asText();
        sendJson(post("/v1/lab/items/" + item + "/result", tech1), Map.of("numeric", 13.2), 200);
        send(post("/v1/lab/items/" + item + "/validate", tech2), 200);
        return item;
    }

    @Test
    void anInvitationIsOneTimeNeedsTheDateOfBirthAndGivesAPatientOnlyTheirOwnReleasedResults() throws Exception {
        Org org = newOrg("portal");
        String records = userWithRole(org, "RECORDS_OFFICER", "RECORDS_OFFICER");
        String doctor = userWithRole(org, "DOCTOR");
        String tech1 = userWithRole(org, "LAB_TECHNOLOGIST", "LAB_TECHNOLOGIST");
        String tech2 = userWithRole(org, "LAB_TECHNOLOGIST", "LAB_TECHNOLOGIST");
        UUID mine = newPatient(org, "Mine", "1990-04-04");
        UUID other = newPatient(org, "Other", "1991-05-05");
        String test = create("/v1/lab/tests", org.token(), Map.of("code", "HB", "name", "Haemoglobin", "unit", "g/dL", "refLow", 12, "refHigh", 17)).get("id").asText();

        // Only staff with the portal permission issue invitations; a doctor cannot.
        sendJson(post("/v1/portal/invitations", doctor), Map.of("patientId", mine.toString()), 403);
        JsonNode inv = create("/v1/portal/invitations", records, Map.of("patientId", mine.toString()));
        String code = inv.get("code").asText();
        assertThat(code).matches("[A-Z0-9]{5}-[A-Z0-9]{5}");
        // Wrong organisation, wrong code, wrong date of birth, weak password: all refused without saying which.
        Map<String, Object> good = Map.of("organisation", slug(org), "code", code, "birthDate", "1990-04-04", "login", "mine.patient@example.org", "password", "a-long-password-1");
        sendJson(post("/portal/auth/activate", null), Map.of("organisation", "no-such-org", "code", code, "birthDate", "1990-04-04", "login", "x@example.org", "password", "a-long-password-1"), 400);
        sendJson(post("/portal/auth/activate", null), Map.of("organisation", slug(org), "code", "AAAAA-AAAAA", "birthDate", "1990-04-04", "login", "x@example.org", "password", "a-long-password-1"), 400);
        JsonNode wrongDob = sendJson(post("/portal/auth/activate", null), Map.of("organisation", slug(org), "code", code, "birthDate", "1990-04-05", "login", "x@example.org", "password", "a-long-password-1"), 400);
        assertThat(wrongDob.get("code").asText()).isEqualTo("invalid_invitation");
        sendJson(post("/portal/auth/activate", null), Map.of("organisation", slug(org), "code", code, "birthDate", "1990-04-04", "login", "x@example.org", "password", "short"), 400);
        sendJson(post("/portal/auth/activate", null), Map.of("organisation", slug(org), "code", code, "birthDate", "1990-04-04", "login", "not a login", "password", "a-long-password-1"), 400);
        sendJson(post("/portal/auth/activate", null), good, 200);
        // One time only.
        sendJson(post("/portal/auth/activate", null), good, 400);
        sendJson(post("/v1/portal/invitations", records), Map.of("patientId", mine.toString()), 409);

        // Sign-in.
        sendJson(post("/portal/auth/login", null), Map.of("organisation", slug(org), "login", "mine.patient@example.org", "password", "wrong-password-xx"), 401);
        String token = portalLogin(org, "Mine.Patient@Example.org", "a-long-password-1");
        assertThat(portal("/me", token).get("givenName").asText()).isEqualTo("Mine");

        // Results: nothing until a clinician releases; the other patient's results never appear.
        String item = validatedLab(org, doctor, tech1, tech2, mine, test);
        String otherItem = validatedLab(org, doctor, tech1, tech2, other, test);
        assertThat(portal("/results", token).get("data")).isEmpty();
        sendJson(post("/v1/portal/lab-items/" + item + "/release", tech1), Map.of(), 403);
        send(post("/v1/portal/lab-items/" + item + "/release", doctor), 200);
        send(post("/v1/portal/lab-items/" + otherItem + "/release", doctor), 200);
        JsonNode results = portal("/results", token).get("data");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("test").asText()).isEqualTo("Haemoglobin");
        assertThat(results.get(0).get("valueNumeric").decimalValue()).isEqualByComparingTo("13.2");
        // Withdrawing hides it again; amending a released result withdraws the release.
        send(post("/v1/portal/lab-items/" + item + "/withdraw", doctor), 200);
        assertThat(portal("/results", token).get("data")).isEmpty();
        send(post("/v1/portal/lab-items/" + item + "/release", doctor), 200);
        assertThat(portal("/results", token).get("data")).hasSize(1);
        sendJson(post("/v1/lab/items/" + item + "/amend", tech2), Map.of("numeric", 11.0, "reason", "Transcription error"), 200);
        assertThat(portal("/results", token).get("data")).isEmpty();
        sendJson(post("/v1/portal/lab-items/" + item + "/release", doctor), Map.of(), 409);
        // The patient's reads are on the audit trail.
        assertThat(fetch("/v1/audit/events?entityType=patient&entityId=" + mine, org.token()).findValuesAsText("action")).contains("portal.invite", "portal.read", "portal.release", "portal.withdraw");
    }

    @Test
    void tokensDoNotCrossBetweenStaffAndPatientsOrOrganisations() throws Exception {
        Org a = newOrg("portal-a");
        Org b = newOrg("portal-b");
        UUID pa = newPatient(a, "Alpha", "1990-04-04");
        UUID pb = newPatient(b, "Beta", "1990-04-04");
        String token = activate(a, pa, "alpha@example.org", "a-long-password-1");
        activate(b, pb, "beta@example.org", "a-long-password-1");
        // A portal token opens nothing on the staff API or the FHIR interface; a staff token opens nothing in the portal.
        mvc.perform(get("/v1/patients", token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/auth/me", token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/fhir/r4/metadata", token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/portal/me", a.token())).andExpect(status().isUnauthorized());
        mvc.perform(get("/portal/me", "garbage")).andExpect(status().isUnauthorized());
        // Org B's login name does not work against org A, and the other way round.
        sendJson(post("/portal/auth/login", null), Map.of("organisation", slug(a), "login", "beta@example.org", "password", "a-long-password-1"), 401);
        sendJson(post("/portal/auth/login", null), Map.of("organisation", slug(b), "login", "alpha@example.org", "password", "a-long-password-1"), 401);
        // The same login name can exist in two organisations.
        UUID pa2 = newPatient(a, "Alpha2", "1990-04-04");
        UUID pb2 = newPatient(b, "Beta2", "1990-04-04");
        activate(a, pa2, "shared@example.org", "a-long-password-1");
        activate(b, pb2, "shared@example.org", "a-long-password-1");
        // Within one organisation a login is unique.
        UUID pa3 = newPatient(a, "Alpha3", "1990-04-04");
        String code = create("/v1/portal/invitations", a.token(), Map.of("patientId", pa3.toString())).get("code").asText();
        JsonNode taken = sendJson(post("/portal/auth/activate", null), Map.of("organisation", slug(a), "code", code, "birthDate", "1990-04-04", "login", "shared@example.org", "password", "a-long-password-1"), 409);
        assertThat(taken.get("code").asText()).isEqualTo("login_taken");
        // A patient of org A is invisible to org B's staff for invitations.
        sendJson(post("/v1/portal/invitations", b.token()), Map.of("patientId", pa3.toString()), 404);
    }

    @Test
    void fiveWrongPasswordsLockTheAccountAndFiveWrongBirthDatesBurnTheInvitation() throws Exception {
        Org org = newOrg("portal-lock");
        UUID p = newPatient(org, "Lock", "1990-04-04");
        activate(org, p, "lock@example.org", "a-long-password-1");
        for (int i = 0; i < 5; i++) {
            sendJson(post("/portal/auth/login", null), Map.of("organisation", slug(org), "login", "lock@example.org", "password", "wrong-password-xx"), 401);
        }
        JsonNode locked = sendJson(post("/portal/auth/login", null), Map.of("organisation", slug(org), "login", "lock@example.org", "password", "a-long-password-1"), 401);
        assertThat(locked.get("code").asText()).isEqualTo("account_locked");
        // Staff can unlock by re-enabling.
        assertThat(fetch("/v1/portal/accounts/" + p, org.token()).get("hasAccount").asBoolean()).isTrue();
        send(post("/v1/portal/accounts/" + p + "/disable", org.token()), 200);
        JsonNode disabled = sendJson(post("/portal/auth/login", null), Map.of("organisation", slug(org), "login", "lock@example.org", "password", "a-long-password-1"), 403);
        assertThat(disabled.get("code").asText()).isEqualTo("account_disabled");
        send(post("/v1/portal/accounts/" + p + "/enable", org.token()), 200);
        portalLogin(org, "lock@example.org", "a-long-password-1");
        // Burning an invitation by guessing the date of birth.
        UUID q = newPatient(org, "Burn", "1990-04-04");
        String code = create("/v1/portal/invitations", org.token(), Map.of("patientId", q.toString())).get("code").asText();
        for (int i = 0; i < 5; i++) {
            sendJson(post("/portal/auth/activate", null), Map.of("organisation", slug(org), "code", code, "birthDate", "1990-04-0" + (i + 5), "login", "burn@example.org", "password", "a-long-password-1"), 400);
        }
        sendJson(post("/portal/auth/activate", null), Map.of("organisation", slug(org), "code", code, "birthDate", "1990-04-04", "login", "burn@example.org", "password", "a-long-password-1"), 400);
        // A fresh invitation works and the old code stays dead.
        String fresh = create("/v1/portal/invitations", org.token(), Map.of("patientId", q.toString())).get("code").asText();
        assertThat(fresh).isNotEqualTo(code);
        sendJson(post("/portal/auth/activate", null), Map.of("organisation", slug(org), "code", fresh, "birthDate", "1990-04-04", "login", "burn@example.org", "password", "a-long-password-1"), 200);
    }

    @Test
    void appointmentRequestsAreBoundedOwnedAndAnsweredByStaff() throws Exception {
        Org org = newOrg("portal-req");
        UUID p = newPatient(org, "Req", "1990-04-04");
        UUID other = newPatient(org, "Req2", "1990-04-04");
        String token = activate(org, p, "req@example.org", "a-long-password-1");
        String otherToken = activate(org, other, "req2@example.org", "a-long-password-1");
        String facility = org.facilityId().toString();
        LocalDate today = LocalDate.now();
        assertThat(portal("/facilities", token)).hasSize(1);
        sendJson(post("/portal/appointment-requests", token), Map.of("facilityId", facility, "preferredDate", today.minusDays(1).toString(), "reason", "Review"), 400);
        sendJson(post("/portal/appointment-requests", token), Map.of("facilityId", facility, "preferredDate", today.plusDays(200).toString(), "reason", "Review"), 400);
        sendJson(post("/portal/appointment-requests", token), Map.of("facilityId", UUID.randomUUID().toString(), "preferredDate", today.plusDays(3).toString(), "reason", "Review"), 404);
        String first = sendJson(post("/portal/appointment-requests", token), Map.of("facilityId", facility, "preferredDate", today.plusDays(3).toString(), "reason", "Blood pressure review"), 201).get("id").asText();
        sendJson(post("/portal/appointment-requests", token), Map.of("facilityId", facility, "preferredDate", today.plusDays(4).toString(), "reason", "Second request"), 201);
        sendJson(post("/portal/appointment-requests", token), Map.of("facilityId", facility, "preferredDate", today.plusDays(5).toString(), "reason", "Third request"), 201);
        sendJson(post("/portal/appointment-requests", token), Map.of("facilityId", facility, "preferredDate", today.plusDays(6).toString(), "reason", "Fourth request"), 409);
        assertThat(portal("/appointment-requests", token)).hasSize(3);
        assertThat(portal("/appointment-requests", otherToken)).isEmpty();
        // Someone else's request cannot be cancelled; your own can, once.
        sendJson(post("/portal/appointment-requests/" + first + "/cancel", otherToken), Map.of(), 404);
        sendJson(post("/portal/appointment-requests/" + first + "/cancel", token), Map.of(), 200);
        sendJson(post("/portal/appointment-requests/" + first + "/cancel", token), Map.of(), 409);
        // Staff see the waiting ones and answer.
        JsonNode waiting = fetch("/v1/portal/requests?facilityId=" + facility + "&status=REQUESTED", org.token());
        assertThat(waiting).hasSize(2);
        String id = waiting.get(0).get("id").asText();
        sendJson(post("/v1/portal/requests/" + id + "/resolve", org.token()), Map.of("status", "SCHEDULED", "note", "x"), 400);
        sendJson(post("/v1/portal/requests/" + id + "/resolve", org.token()), Map.of("status", "SCHEDULED", "note", "Booked for Tuesday 10:00, clinic 2"), 200);
        sendJson(post("/v1/portal/requests/" + id + "/resolve", org.token()), Map.of("status", "DECLINED", "note", "again please"), 409);
        JsonNode mine = portal("/appointment-requests", token);
        boolean answered = false;
        for (JsonNode r : mine) {
            if (r.get("id").asText().equals(id)) {
                answered = r.get("status").asText().equals("SCHEDULED") && r.get("responseNote").asText().contains("Tuesday");
            }
        }
        assertThat(answered).isTrue();
        // Medications and allergies are read-only views of the patient's own record.
        assertThat(portal("/medications", token)).isEmpty();
        assertThat(portal("/allergies", token)).isEmpty();
        assertThat(portal("/appointments", token)).isEmpty();
    }

    @Test
    void staffCanResetAnAccountSoAPatientWhoForgotTheirPasswordCanBeInvitedAgain() throws Exception {
        Org org = newOrg("portal-reset");
        UUID p = newPatient(org, "Forgot", "1990-04-04");
        String token = activate(org, p, "forgot@example.org", "a-long-password-1");
        mvc.perform(get("/portal/me", token)).andExpect(status().isOk());
        send(post("/v1/portal/accounts/" + p + "/reset", org.token()), 200);
        assertThat(fetch("/v1/portal/accounts/" + p, org.token()).get("hasAccount").asBoolean()).isFalse();
        sendJson(post("/portal/auth/login", null), Map.of("organisation", slug(org), "login", "forgot@example.org", "password", "a-long-password-1"), 401);
        sendJson(post("/v1/portal/accounts/" + p + "/reset", org.token()), Map.of(), 404);
        // A fresh invitation sets a new password.
        String again = activate(org, p, "forgot@example.org", "another-long-password-2");
        mvc.perform(get("/portal/me", again)).andExpect(status().isOk());
        assertThat(fetch("/v1/audit/events?entityType=patient&entityId=" + p, org.token()).findValuesAsText("action")).contains("portal.account.reset");
    }
}
