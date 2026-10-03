package com.mara.identity.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mara.identity.TestCredentials;
import com.mara.platform.credential.OperatorToken;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Operator and service credentials through the real application and a real PostgreSQL: what each
 * scope allows, that a tenant-bound credential cannot leave its tenant, expiry, revocation,
 * rotation with an overlap, the audit trail, and that the application's own database role cannot
 * touch the credential tables. Needs {@code -Dmara.test.jdbc.url}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("operator and service credentials")
class CredentialLifecycleTest {

    private static final String JDBC_URL = System.getProperty("mara.test.jdbc.url");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        org.junit.jupiter.api.Assumptions.assumeTrue(JDBC_URL != null && !JDBC_URL.isBlank(), "needs -Dmara.test.jdbc.url");
        registry.add("mara.datasource.owner.url", () -> JDBC_URL);
        registry.add("mara.datasource.owner.username", () -> System.getProperty("mara.test.owner.user", "mara_owner"));
        registry.add("mara.datasource.owner.password", () -> System.getProperty("mara.test.owner.password", "owner-secret"));
        registry.add("mara.datasource.app.url", () -> JDBC_URL);
        registry.add("mara.datasource.app.username", () -> "mara_app");
        registry.add("mara.datasource.app.password", () -> "app-secret");
        registry.add("mara.credential.seed.core", () -> TestCredentials.SVC_CORE_TOKEN);
        registry.add("mara.credential.seed.sync", () -> TestCredentials.SVC_SYNC_TOKEN);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OperatorCredentialService credentials;
    @Autowired @Qualifier("ownerDataSource") DataSource ownerDs;
    @Autowired DataSource appDs;

    private JdbcTemplate owner;

    @BeforeEach
    void setUp() {
        owner = new JdbcTemplate(ownerDs);
        TestCredentials.installPlatformOperator(owner);
    }

    // ---------------------------------------------------------------- helpers

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private ResultActions call(String method, String path, String token, String tenant, Object payload) throws Exception {
        MockHttpServletRequestBuilder b = method.equals("GET") ? get(path) : post(path);
        b.contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            b.header("Authorization", bearer(token));
        }
        if (tenant != null) {
            b.header("X-Mara-Tenant", tenant);
        }
        if (payload != null) {
            b.content(json.writeValueAsString(payload));
        }
        return mvc.perform(b);
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private JsonNode issue(String label, String tenant, List<String> scopes, Integer hours) throws Exception {
        var req = new java.util.HashMap<String, Object>();
        req.put("label", label);
        req.put("tenantId", tenant);
        req.put("scopes", scopes);
        req.put("expiresInHours", hours);
        return body(call("POST", "/v1/admin/credentials", TestCredentials.PLATFORM_TOKEN, null, req).andExpect(status().isCreated()));
    }

    private String createTenant(String name) throws Exception {
        var r = call("POST", "/v1/admin/tenants", TestCredentials.PLATFORM_TOKEN, null, Map.ofEntries(
                Map.entry("legalName", name + " Ltd"), Map.entry("tradingName", name), Map.entry("countryCode", "KE"),
                Map.entry("currency", "KES"), Map.entry("licensedTerminals", 2), Map.entry("branchName", "Main"),
                Map.entry("timezone", "Africa/Nairobi"), Map.entry("ownerName", "Owner"),
                Map.entry("ownerStaffNumber", "1"), Map.entry("ownerPin", "483920")));
        return body(r.andExpect(status().isCreated())).get("tenantId").asText();
    }

    private long countAudit(String action, String detailLike) {
        return owner.queryForObject("SELECT count(*) FROM operator_credential_audit WHERE action = ? AND detail LIKE ?",
                Long.class, action, detailLike);
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("a tenant-bound credential works for its own tenant, is forced onto it, and cannot name or reach another")
    void tenantBoundCredentialCannotLeaveItsTenant() throws Exception {
        String a = createTenant("Alpha " + UUID.randomUUID().toString().substring(0, 6));
        String b = createTenant("Beta " + UUID.randomUUID().toString().substring(0, 6));
        String token = issue("alpha-console", a, List.of("admin:read", "admin:write"), 2).get("credential").asText();

        // its own tenant, named or not (the credential supplies it)
        assertThat(body(call("GET", "/v1/admin/staff", token, a, null).andExpect(status().isOk()))).hasSize(1);
        JsonNode unnamed = body(call("GET", "/v1/admin/staff", token, null, null).andExpect(status().isOk()));
        assertThat(unnamed).hasSize(1);

        // another tenant by name: refused, and it is audited
        call("GET", "/v1/admin/staff", token, b, null).andExpect(status().isForbidden());
        assertThat(countAudit("DENIED", "tenant_mismatch%")).isPositive();

        // and the platform operator, naming B, sees B's staff only: the two are different people
        JsonNode bStaff = body(call("GET", "/v1/admin/staff", TestCredentials.PLATFORM_TOKEN, b, null).andExpect(status().isOk()));
        assertThat(bStaff.toString()).isNotEqualTo(unnamed.toString());

        // it can write in its tenant, but cannot create tenants or manage credentials
        call("POST", "/v1/admin/branches", token, a, Map.of("name", "Second", "timezone", "Africa/Nairobi"))
                .andExpect(status().isCreated());
        call("POST", "/v1/admin/tenants", token, null, Map.of("legalName", "x")).andExpect(status().isForbidden());
        call("GET", "/v1/admin/credentials", token, a, null).andExpect(status().isForbidden());
        call("POST", "/v1/admin/credentials", token, a, Map.of("label", "x", "scopes", List.of("admin:read")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a read-only credential reads and cannot write; a credential for one scope cannot use another's door")
    void scopesAreEnforcedPerEndpoint() throws Exception {
        String a = createTenant("Scoped " + UUID.randomUUID().toString().substring(0, 6));
        String reader = issue("reader", a, List.of("admin:read"), 1).get("credential").asText();
        call("GET", "/v1/admin/terminals", reader, a, null).andExpect(status().isOk());
        call("POST", "/v1/admin/branches", reader, a, Map.of("name", "Nope", "timezone", "Africa/Nairobi"))
                .andExpect(status().isForbidden());
        // an operator credential does not open the service-to-service door, nor the verify endpoint
        call("GET", "/v1/internal/terminals/TERM-00000000000000000000", reader, null, null).andExpect(status().isForbidden());
        call("POST", "/v1/internal/credentials/verify", reader, null,
                Map.of("credential", reader, "requiredScope", "admin:read")).andExpect(status().isForbidden());
        // an endpoint nobody has assigned a scope to is closed
        call("GET", "/v1/admin/credentials", TestCredentials.PLATFORM_TOKEN, null, null).andExpect(status().isOk());
        call("GET", "/v1/internal/unlisted", TestCredentials.PLATFORM_TOKEN, null, null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("what may be issued is limited by kind and binding, and the secret is shown once")
    void issueRules() throws Exception {
        // platform-only scopes cannot be tenant-bound; service scopes cannot be issued to operators
        call("POST", "/v1/admin/credentials", TestCredentials.PLATFORM_TOKEN, null,
                Map.of("label", "x", "tenantId", "TEN-1", "scopes", List.of("platform:tenants"))).andExpect(status().isUnprocessableEntity());
        call("POST", "/v1/admin/credentials", TestCredentials.PLATFORM_TOKEN, null,
                Map.of("label", "x", "scopes", List.of("terminals:lookup"))).andExpect(status().isUnprocessableEntity());
        call("POST", "/v1/admin/credentials", TestCredentials.PLATFORM_TOKEN, null,
                Map.of("label", "x", "scopes", List.of("admin:everything"))).andExpect(status().isUnprocessableEntity());
        call("POST", "/v1/admin/credentials", TestCredentials.PLATFORM_TOKEN, null,
                Map.of("label", "x", "scopes", List.of("admin:read"), "expiresInHours", 721)).andExpect(status().isBadRequest());
        call("POST", "/v1/admin/credentials", TestCredentials.PLATFORM_TOKEN, null,
                Map.of("label", " ", "scopes", List.of("admin:read"))).andExpect(status().isBadRequest());

        JsonNode issued = issue("one-time", null, List.of("admin:read"), 1);
        String token = issued.get("credential").asText();
        // the database holds a hash, never the secret; the listing never shows either
        String secret = OperatorToken.parse(token).orElseThrow().secret();
        assertThat(owner.queryForObject("SELECT count(*) FROM operator_credential WHERE encode(secret_hash,'escape') LIKE ?",
                Long.class, "%" + secret.substring(0, 10) + "%")).isZero();
        String listing = call("GET", "/v1/admin/credentials", TestCredentials.PLATFORM_TOKEN, null, null)
                .andReturn().getResponse().getContentAsString();
        assertThat(listing).contains("one-time").doesNotContain(secret).doesNotContain("secret_hash");
    }

    @Test
    @DisplayName("an expired credential and a revoked one are refused, and revoking twice says so")
    void expiryAndRevocation() throws Exception {
        // an expired one, as the owner would see it after the fact
        var minted = OperatorToken.mint();
        owner.update("""
                INSERT INTO operator_credential (key_id, secret_hash, label, kind, scopes, created_by, created_at, expires_at)
                VALUES (?, ?, 'expired', 'OPERATOR', ARRAY['admin:read'], 'test', now() - interval '2 hours', now() - interval '1 hour')""",
                minted.keyId(), minted.secretHash());
        call("GET", "/v1/admin/branches", minted.token(), "TEN-x", null).andExpect(status().isUnauthorized());
        assertThat(countAudit("DENIED", "expired%")).isPositive();

        JsonNode issued = issue("to-revoke", null, List.of("admin:read"), 1);
        String token = issued.get("credential").asText();
        call("GET", "/v1/admin/credentials", token, null, null).andExpect(status().isForbidden());   // valid, wrong scope
        call("POST", "/v1/admin/credentials/revoke", TestCredentials.PLATFORM_TOKEN, null, Map.of("id", issued.get("id").asText()))
                .andExpect(status().isNoContent());
        call("GET", "/v1/admin/branches", token, "TEN-x", null).andExpect(status().isUnauthorized());
        call("POST", "/v1/admin/credentials/revoke", TestCredentials.PLATFORM_TOKEN, null, Map.of("id", issued.get("id").asText()))
                .andExpect(status().isNotFound());
        assertThat(countAudit("REVOKED", "%")).isPositive();
    }

    @Test
    @DisplayName("rotation yields a working replacement, and the old one stops when its grace ends")
    void rotation() throws Exception {
        String a = createTenant("Rot " + UUID.randomUUID().toString().substring(0, 6));
        JsonNode old = issue("rotating", a, List.of("admin:read"), 6);
        String oldToken = old.get("credential").asText();
        JsonNode next = body(call("POST", "/v1/admin/credentials/rotate", TestCredentials.PLATFORM_TOKEN, null,
                Map.of("id", old.get("id").asText(), "graceMinutes", 30)).andExpect(status().isCreated()));
        String newToken = next.get("credential").asText();
        assertThat(newToken).isNotEqualTo(oldToken);
        // both work during the grace
        call("GET", "/v1/admin/branches", oldToken, a, null).andExpect(status().isOk());
        call("GET", "/v1/admin/branches", newToken, a, null).andExpect(status().isOk());
        // the replacement keeps label, tenant and scopes
        assertThat(owner.queryForObject("SELECT tenant_id FROM operator_credential WHERE id = ?::uuid", String.class,
                next.get("id").asText())).isEqualTo(a);
        // grace 0: the old one ends at once
        JsonNode old2 = issue("rotating-now", a, List.of("admin:read"), 6);
        call("POST", "/v1/admin/credentials/rotate", TestCredentials.PLATFORM_TOKEN, null,
                Map.of("id", old2.get("id").asText(), "graceMinutes", 0)).andExpect(status().isCreated());
        call("GET", "/v1/admin/branches", old2.get("credential").asText(), a, null).andExpect(status().isUnauthorized());
        assertThat(countAudit("ROTATED", "%")).isPositive();
    }

    @Test
    @DisplayName("the audit trail is append-only and the application role cannot read or change the credential tables")
    void auditAndPrivileges() throws Exception {
        issue("audited", null, List.of("admin:read"), 1);
        JsonNode trail = body(call("GET", "/v1/admin/credentials/audit?limit=50", TestCredentials.PLATFORM_TOKEN, null, null)
                .andExpect(status().isOk()));
        assertThat(trail.toString()).contains("ISSUED");
        assertThatThrownBy(() -> owner.update("UPDATE operator_credential_audit SET detail = 'x'")).hasMessageContaining("append-only");
        assertThatThrownBy(() -> owner.update("DELETE FROM operator_credential_audit")).hasMessageContaining("append-only");
        assertThatThrownBy(() -> owner.execute("TRUNCATE operator_credential_audit")).hasMessageContaining("append-only");
        // mara_app has no privilege on either table; only the narrow functions
        var app = new JdbcTemplate(appDs);
        assertThatThrownBy(() -> app.queryForList("SELECT * FROM operator_credential")).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> app.queryForList("SELECT * FROM operator_credential_audit")).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> app.update("UPDATE operator_credential SET revoked_at = NULL")).rootCause().hasMessageContaining("permission denied");
    }

    @Test
    @DisplayName("the verify endpoint answers for services, with the reason, and only to a service credential")
    void verifyEndpoint() throws Exception {
        JsonNode good = issue("verifiable", null, List.of("admin:read"), 1);
        String token = good.get("credential").asText();
        var req = new java.util.HashMap<String, Object>();
        req.put("credential", token);
        req.put("requiredScope", "admin:read");
        JsonNode ok = body(call("POST", "/v1/internal/credentials/verify", TestCredentials.SVC_CORE_TOKEN, null, req)
                .andExpect(status().isOk()));
        assertThat(ok.get("ok").asBoolean()).isTrue();
        assertThat(ok.get("label").asText()).isEqualTo("verifiable");

        req.put("requiredScope", "admin:write");
        JsonNode denied = body(call("POST", "/v1/internal/credentials/verify", TestCredentials.SVC_SYNC_TOKEN, null, req).andExpect(status().isOk()));
        assertThat(denied.get("ok").asBoolean()).isFalse();
        assertThat(denied.get("status").asInt()).isEqualTo(403);
        assertThat(denied.get("reason").asText()).isEqualTo("scope_denied");

        req.put("credential", OperatorToken.mint().token());
        JsonNode unknown = body(call("POST", "/v1/internal/credentials/verify", TestCredentials.SVC_CORE_TOKEN, null, req).andExpect(status().isOk()));
        assertThat(unknown.get("status").asInt()).isEqualTo(401);

        // a malformed or missing service credential gets nothing
        call("POST", "/v1/internal/credentials/verify", null, null, req).andExpect(status().isUnauthorized());
        call("POST", "/v1/internal/credentials/verify", "not-a-credential", null, req).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("seeding registers a credential, never resurrects a revoked one, and revokes what rotation left out")
    void seeding() {
        String label = "svc-test-" + UUID.randomUUID().toString().substring(0, 6);
        var one = OperatorToken.mint();
        var two = OperatorToken.mint();
        var p1 = OperatorToken.parse(one.token()).orElseThrow();
        var p2 = OperatorToken.parse(two.token()).orElseThrow();
        var life = java.time.Instant.now().plus(java.time.Duration.ofDays(30));
        List<String> scopes = List.of("terminals:lookup");

        credentials.seed(label, "SERVICE", scopes, p1, List.of(p1.keyId()), life);
        assertThat(credentials.lookup(p1.keyId()).orElseThrow().revokedAt()).isNull();

        // rotate: new current, old kept as previous: both live
        credentials.seed(label, "SERVICE", scopes, p2, List.of(p2.keyId(), p1.keyId()), life);
        assertThat(credentials.lookup(p1.keyId()).orElseThrow().revokedAt()).isNull();
        assertThat(credentials.lookup(p2.keyId()).orElseThrow().revokedAt()).isNull();

        // previous dropped from the environment: the old one is revoked
        int revoked = credentials.seed(label, "SERVICE", scopes, p2, List.of(p2.keyId()), life);
        assertThat(revoked).isEqualTo(1);
        assertThat(credentials.lookup(p1.keyId()).orElseThrow().revokedAt()).isNotNull();

        // seeding the revoked one again does not bring it back
        credentials.seed(label, "SERVICE", scopes, p1, List.of(p1.keyId(), p2.keyId()), life);
        assertThat(credentials.lookup(p1.keyId()).orElseThrow().revokedAt()).isNotNull();

        // a service credential cannot be created with operator scopes, even by the seed path's rules
        assertThat(com.mara.platform.credential.Scopes.problem("SERVICE", null, List.of("admin:write"))).isNotNull();
    }
}
