package com.hms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.platform.audit.AuditService;
import com.hms.platform.tenancy.TenantContext;
import com.hms.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class FoundationTest extends IntegrationTest {

    @Autowired JdbcClient jdbc;
    @Autowired PlatformTransactionManager txm;
    @Autowired PasswordEncoder encoder;
    @Autowired AuditService audit;

    private <T> T asTenant(UUID orgId, java.util.function.Supplier<T> work) {
        TenantContext.set(new TenantContext.Tenant(orgId, null, Set.of(), Set.of()));
        try {
            return new TransactionTemplate(txm).execute(s -> work.get());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void onboardingCreatesAWorkingOrganisationAndSignIn() throws Exception {
        Org org = newOrg("onboard");
        var login = mvc.perform(post("/v1/auth/login", null).content(json.writeValueAsString(Map.of("email", org.email(), "password", org.password()))))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(login.getResponse().getContentAsString());
        assertThat(body.get("facilities")).hasSize(1);
        assertThat(body.get("facilities").get(0).get("name").asText()).isEqualTo("Main Hospital");
        List<String> permissions = json.convertValue(body.get("permissions"), List.class);
        assertThat(permissions).contains("patients:write", "roles:manage", "audit:read");
    }

    @Test
    void aWrongPasswordIsRefusedAndTheAccountLocksAfterRepeatedFailures() throws Exception {
        Org org = newOrg("lock");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/v1/auth/login", null).content(json.writeValueAsString(Map.of("email", org.email(), "password", "wrong-password-" + i))))
                    .andExpect(status().isUnauthorized());
        }
        // Even the right password is refused while the lock holds.
        mvc.perform(post("/v1/auth/login", null).content(json.writeValueAsString(Map.of("email", org.email(), "password", org.password()))))
                .andExpect(status().isUnauthorized());
        // An unknown email gets the same answer, so accounts cannot be enumerated.
        mvc.perform(post("/v1/auth/login", null).content(json.writeValueAsString(Map.of("email", "nobody@example.org", "password", "whatever-123456"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestsWithoutATokenOrWithAForgedOneAreRejected() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/patients")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/patients", "not.a.token")).andExpect(status().isUnauthorized());
    }

    @Test
    void theDatabaseItselfHidesOneOrganisationFromAnother() throws Exception {
        Org a = newOrg("iso-a");
        Org b = newOrg("iso-b");
        mvc.perform(post("/v1/patients", a.token()).content(json.writeValueAsString(patient(a.facilityId(), "Wanjiku", "Kamau", "1990-04-12"))))
                .andExpect(status().isCreated());
        // Straight to SQL as the application role, bypassing every controller check:
        long seenByB = asTenant(b.orgId(), () -> jdbc.sql("SELECT count(*) FROM patients").query(Long.class).single());
        long seenByA = asTenant(a.orgId(), () -> jdbc.sql("SELECT count(*) FROM patients").query(Long.class).single());
        long seenByNobody = new TransactionTemplate(txm).execute(s -> jdbc.sql("SELECT count(*) FROM patients").query(Long.class).single());
        assertThat(seenByA).isEqualTo(1);
        assertThat(seenByB).isZero();
        assertThat(seenByNobody).isZero();
        // And a write aimed at another organisation is refused by the policy, not by the application.
        assertThrows(Exception.class, () -> asTenant(b.orgId(), () -> jdbc.sql(
                "INSERT INTO facilities (org_id, name) VALUES (?, 'sneaky')").param(a.orgId()).update()));
    }

    @Test
    void theApplicationRoleCannotSeeOtherOrganisationsNamesEither() throws Exception {
        Org a = newOrg("names-a");
        Org b = newOrg("names-b");
        long visible = asTenant(a.orgId(), () -> jdbc.sql("SELECT count(*) FROM organisations").query(Long.class).single());
        assertThat(visible).isEqualTo(1);
        long none = new TransactionTemplate(txm).execute(s -> jdbc.sql("SELECT count(*) FROM organisations").query(Long.class).single());
        assertThat(none).isZero();
    }

    @Test
    void permissionsComeFromTheOrganisationsRolesAndChangeWhenTheRoleChanges() throws Exception {
        Org org = newOrg("rbac");
        UUID auditorId = UUID.randomUUID();
        String hash = encoder.encode("auditor-password-1");
        asOwner("INSERT INTO practitioners (id, org_id, email, password_hash, full_name, cadre) VALUES ('" + auditorId + "', '" + org.orgId()
                        + "', 'auditor-" + auditorId + "@example.org', '" + hash + "', 'Audrey Auditor', 'ADMINISTRATIVE')",
                "INSERT INTO practitioner_roles (org_id, practitioner_id, role_key) VALUES ('" + org.orgId() + "', '" + auditorId + "', 'AUDITOR')",
                "INSERT INTO practitioner_facilities (org_id, practitioner_id, facility_id) VALUES ('" + org.orgId() + "', '" + auditorId + "', '" + org.facilityId() + "')");
        String token = login("auditor-" + auditorId + "@example.org", "auditor-password-1");

        mvc.perform(get("/v1/patients", token)).andExpect(status().isOk());
        mvc.perform(post("/v1/patients", token).content(json.writeValueAsString(patient(org.facilityId(), "Test", "Person", "1990-01-01"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/audit/verify", token)).andExpect(status().isOk());

        // Give the role the write permission in the organisation's own data: the next request obeys it.
        asOwner("UPDATE roles SET permissions = permissions || '[\"patients:write\"]'::jsonb WHERE org_id = '" + org.orgId() + "' AND role_key = 'AUDITOR'");
        mvc.perform(post("/v1/patients", token).content(json.writeValueAsString(patient(org.facilityId(), "Test", "Person", "1990-01-01"))))
                .andExpect(status().isCreated());

        // Disabling the account stops a token that is still cryptographically valid.
        asOwner("UPDATE practitioners SET status = 'DISABLED' WHERE id = '" + auditorId + "'");
        mvc.perform(get("/v1/patients", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void theAuditChainVerifiesAndDetectsTampering() throws Exception {
        Org org = newOrg("audit");
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/v1/patients", org.token()).content(json.writeValueAsString(patient(org.facilityId(), "Person" + i, "Audit" + i, "1985-0" + (i + 1) + "-15"))))
                    .andExpect(status().isCreated());
        }
        var ok = json.readTree(mvc.perform(get("/v1/audit/verify", org.token())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(ok).isNotEmpty();
        ok.forEach(v -> assertThat(v.get("intact").asBoolean()).isTrue());
        assertThat(ok.get(0).get("checked").asLong()).isGreaterThanOrEqualTo(3);

        // The table refuses edits, even from its owner...
        assertThrows(Exception.class,
                () -> asOwner("UPDATE audit_event SET action = 'patient.read' WHERE org_id = '" + org.orgId() + "'"));
        // ...so a determined attacker has to switch the guard off first, and the chain still catches it.
        asOwner("ALTER TABLE audit_event DISABLE TRIGGER audit_event_append_only",
                "UPDATE audit_event SET action = 'patient.deleted-quietly' WHERE id = (SELECT min(id) FROM audit_event WHERE org_id = '" + org.orgId() + "' AND action = 'patient.create')",
                "ALTER TABLE audit_event ENABLE TRIGGER audit_event_append_only");
        var broken = json.readTree(mvc.perform(get("/v1/audit/verify", org.token())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        boolean anyBroken = false;
        for (JsonNode v : broken) {
            if (!v.get("intact").asBoolean()) {
                anyBroken = true;
                assertThat(v.get("brokenAtSeq").asLong()).isPositive();
            }
        }
        assertThat(anyBroken).as("tampering must be detected").isTrue();
    }
}
