package com.hms;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffAdminTest extends IntegrationTest {

    @Test
    void anAdministratorCreatesStaffWhoThenSignInWithTheirOwnRole() throws Exception {
        Org org = newOrg("staff");
        String nurse = userWithRole(org, "NURSE");
        JsonNode me = fetch("/v1/auth/me", nurse);
        assertThat(json.convertValue(me.get("permissions"), List.class)).contains("clinical:write").doesNotContain("roles:manage", "staff:manage");
        // A nurse cannot administer staff.
        send(get("/v1/staff", nurse), 403);
        JsonNode list = fetch("/v1/staff", org.token());
        assertThat(list.get("data")).hasSize(2);
    }

    @Test
    void noOneCanGrantMoreThanTheyHold() throws Exception {
        Org org = newOrg("escalate");
        // A role that may manage staff but holds nothing else.
        create("/v1/roles", org.token(), Map.of("key", "STAFF_CLERK", "label", "Staff clerk", "permissions", List.of("staff:manage", "staff:read")));
        String clerk = userWithRole(org, "STAFF_CLERK");
        // The clerk may not hand out the doctor role (clinical permissions they lack)...
        sendJson(post("/v1/staff", clerk), Map.of("email", "x-" + UUID.randomUUID() + "@example.org", "fullName", "Some One", "cadre", "DOCTOR",
                "temporaryPassword", "temporary-password-1", "roles", List.of("DOCTOR"), "facilityIds", List.of(org.facilityId())), 403);
        // ...nor promote themselves to administrator.
        UUID clerkId = practitionerIdOf(clerk);
        sendJson(put("/v1/staff/" + clerkId + "/assignment", clerk), Map.of("roles", List.of("ORG_ADMIN"), "facilityIds", List.of(org.facilityId())), 403);
        // Nor create a role with permissions they lack.
        sendJson(post("/v1/roles", clerk), Map.of("key", "SNEAKY", "label", "Sneaky", "permissions", List.of("billing:refund")), 403);
    }

    @Test
    void theLastAdministratorCannotBeDisabledOrDemotedAndSelfDisableIsRefused() throws Exception {
        Org org = newOrg("lastadmin");
        send(post("/v1/staff/" + org.adminId() + "/disable", org.token()), 409);
        sendJson(put("/v1/staff/" + org.adminId() + "/assignment", org.token()), Map.of("roles", List.of("DOCTOR"), "facilityIds", List.of(org.facilityId())), 409);
        // With a second administrator the first can be demoted.
        String second = userWithRole(org, "ORG_ADMIN");
        sendJson(put("/v1/staff/" + org.adminId() + "/assignment", second), Map.of("roles", List.of("DOCTOR"), "facilityIds", List.of(org.facilityId())), 200);
    }

    @Test
    void disablingAccountsStopsAccessAndTheRoleCacheDoesNotKeepIt() throws Exception {
        Org org = newOrg("disable");
        String nurse = userWithRole(org, "NURSE");
        UUID nurseId = practitionerIdOf(nurse);
        send(get("/v1/patients", nurse), 200);
        send(post("/v1/staff/" + nurseId + "/disable", org.token()), 200);
        // The token is still well-formed, but the account is no longer active.
        send(get("/v1/patients", nurse), 401);
        var login = mvc.perform(post("/v1/auth/login", null).content(json.writeValueAsString(Map.of("email", fetch("/v1/staff/" + nurseId, org.token()).get("email").asText(), "password", "temporary-password-1"))))
                .andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(403);
        send(post("/v1/staff/" + nurseId + "/enable", org.token()), 200);
        send(get("/v1/patients", nurse), 200);
    }

    @Test
    void editingARoleChangesWhatItsMembersCanDoAndRoleInUseCannotBeDeleted() throws Exception {
        Org org = newOrg("roles");
        create("/v1/roles", org.token(), Map.of("key", "RECEPTION", "label", "Reception", "permissions", List.of("patients:read")));
        String user = userWithRole(org, "RECEPTION");
        send(get("/v1/patients", user), 200);
        sendJson(post("/v1/patients", user), patient(org.facilityId(), "Amina", "Hassan", "1995-01-01"), 403);
        sendJson(put("/v1/roles/RECEPTION", org.token()), Map.of("label", "Reception", "permissions", List.of("patients:read", "patients:write")), 200);
        sendJson(post("/v1/patients", user), patient(org.facilityId(), "Amina", "Hassan", "1995-01-01"), 201);
        send(delete("/v1/roles/RECEPTION", org.token()), 409);
        send(delete("/v1/roles/DOCTOR", org.token()), 409);
        // The administrator role is fixed.
        sendJson(put("/v1/roles/ORG_ADMIN", org.token()), Map.of("label", "Admin", "permissions", List.of("patients:read")), 409);
        // An unknown permission is rejected.
        sendJson(post("/v1/roles", org.token()), Map.of("key", "BAD", "label", "Bad", "permissions", List.of("made:up")), 400);
    }

    @Test
    void facilitiesCanBeAddedEditedAndDeactivatedButNotAllOfThem() throws Exception {
        Org org = newOrg("fac");
        JsonNode created = create("/v1/facilities", org.token(), Map.of("name", "Kisumu Annex", "mflCode", "20" + (int) (Math.random() * 9000 + 1000), "kephLevel", 3, "ownership", "PUBLIC", "county", "Kisumu"));
        UUID annex = UUID.fromString(created.get("id").asText());
        assertThat(fetch("/v1/facilities", org.token())).hasSize(2);
        // A duplicate MFL code is refused.
        sendJson(post("/v1/facilities", org.token()), Map.of("name", "Other", "mflCode", created.get("mflCode").asText()), 409);
        sendJson(put("/v1/facilities/" + annex, org.token()), Map.of("name", "Kisumu Annex", "kephLevel", 3, "active", false), 200);
        sendJson(put("/v1/facilities/" + org.facilityId(), org.token()), Map.of("name", "Main Hospital", "active", false), 409);
        // A nurse cannot administer facilities.
        sendJson(post("/v1/facilities", userWithRole(org, "NURSE")), Map.of("name", "Nope Clinic"), 403);
    }

    @Test
    void staffAreInvisibleToOtherOrganisationsAndPasswordChangeNeedsTheCurrentOne() throws Exception {
        Org a = newOrg("tenant-a");
        Org b = newOrg("tenant-b");
        send(get("/v1/staff/" + a.adminId(), b.token()), 404);
        assertThat(fetch("/v1/staff", b.token()).get("data")).hasSize(1);
        sendJson(post("/v1/auth/me/password", a.token()), Map.of("currentPassword", "not-it-at-all-123", "newPassword", "a-brand-new-password"), 400);
        sendJson(post("/v1/auth/me/password", a.token()), Map.of("currentPassword", a.password(), "newPassword", "a-brand-new-password"), 204);
        // Changing the password ends every session, the one that changed it included.
        send(get("/v1/auth/me", a.token()), 401);
        String fresh = login(a.email(), "a-brand-new-password");
        // Audit trail records the administration actions and still verifies.
        JsonNode verify = fetch("/v1/audit/verify", fresh);
        assertThat(verify.findValuesAsText("intact")).containsOnly("true");
    }
}
