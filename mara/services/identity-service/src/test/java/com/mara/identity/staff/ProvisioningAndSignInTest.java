package com.mara.identity.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mara.identity.admin.AdminTokenFilter;
import com.mara.identity.admin.PinHasher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The real Spring context against a real PostgreSQL, through HTTP: the layer where every
 * wiring defect in this service has so far hidden. Needs {@code -Dmara.test.jdbc.url}
 * (the same external database the enrolment test uses); skipped without it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("provisioning and staff sign-in, through the real application")
class ProvisioningAndSignInTest {

    private static final String JDBC_URL = System.getProperty("mara.test.jdbc.url");
    private static final String ADMIN = "Bearer test-admin-token-0123456789-abcdef";
    private static final String INTERNAL = "Bearer test-internal-token-0123456789-abc";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        org.junit.jupiter.api.Assumptions.assumeTrue(JDBC_URL != null && !JDBC_URL.isBlank(),
                "needs -Dmara.test.jdbc.url");
        registry.add("mara.datasource.owner.url", () -> JDBC_URL);
        registry.add("mara.datasource.owner.username", () -> System.getProperty("mara.test.owner.user", "mara_owner"));
        registry.add("mara.datasource.owner.password", () -> System.getProperty("mara.test.owner.password", "owner-secret"));
        registry.add("mara.datasource.app.url", () -> JDBC_URL);
        registry.add("mara.datasource.app.username", () -> "mara_app");
        registry.add("mara.datasource.app.password", () -> "app-secret");
        registry.add("mara.admin.token", () -> ADMIN.substring(7));
        registry.add("mara.internal.token", () -> INTERNAL.substring(7));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("ownerDataSource") javax.sql.DataSource owner;

    private JdbcTemplate ownerJdbc;

    @BeforeEach
    void clean() {
        ownerJdbc = new JdbcTemplate(owner);
        ownerJdbc.execute("TRUNCATE enrolment_code, terminal, staff, branch, tenant, audit_log CASCADE");
    }

    // ------------------------------------------------------------------ fixtures

    record Shop(String tenantId, String branchId, String ownerStaffId, String terminalId, KeyPair key) {
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private ResultActions admin(String method, String path, String tenant, Object payload) throws Exception {
        var builder = method.equals("GET") ? get(path) : post(path);
        builder.header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON);
        if (tenant != null) {
            builder.header("X-Mara-Tenant", tenant);
        }
        if (payload != null) {
            builder.content(json.writeValueAsString(payload));
        }
        return mvc.perform(builder);
    }

    private JsonNode createTenant(String trading, int licence) throws Exception {
        return body(admin("POST", "/v1/admin/tenants", null, java.util.Map.ofEntries(
                java.util.Map.entry("legalName", trading + " Ltd"), java.util.Map.entry("tradingName", trading),
                java.util.Map.entry("countryCode", "KE"), java.util.Map.entry("currency", "KES"),
                java.util.Map.entry("licensedTerminals", licence), java.util.Map.entry("branchName", "Main"),
                java.util.Map.entry("timezone", "Africa/Nairobi"), java.util.Map.entry("ownerName", "Owner"),
                java.util.Map.entry("ownerStaffNumber", "1000"), java.util.Map.entry("ownerPin", "7391")))
                .andExpect(status().isCreated()));
    }

    private Shop shopWithTerminal(String trading) throws Exception {
        JsonNode t = createTenant(trading, 3);
        String tenant = t.get("tenantId").asText();
        String branch = t.get("branchId").asText();
        String owner = t.get("ownerStaffId").asText();
        JsonNode issued = body(admin("POST", "/v1/admin/enrolment-codes", tenant,
                java.util.Map.of("branchId", branch, "issuedBy", owner)).andExpect(status().isCreated()));
        KeyPair key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        JsonNode enrolled = body(mvc.perform(post("/v1/enrolment").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                        "code", issued.get("code").asText(),
                        "publicKey", Base64.getEncoder().encodeToString(key.getPublic().getEncoded()),
                        "label", "Lane 1"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()));
        return new Shop(tenant, branch, owner, enrolled.get("terminalId").asText(), key);
    }

    private String addCashier(Shop shop, String number, String pin, String branch) throws Exception {
        JsonNode created = body(admin("POST", "/v1/admin/staff", shop.tenantId(), java.util.Map.of(
                "branchId", branch, "displayName", "Cashier " + number, "role", "CASHIER",
                "staffNumber", number, "pin", pin)).andExpect(status().isCreated()));
        return created.get("staffId").asText();
    }

    private ResultActions signIn(Shop shop, String terminal, String number, String pin, long ts, KeyPair signer) throws Exception {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(signer.getPrivate());
        s.update(StaffSignInService.signedMessage(terminal, number, ts));
        String sig = HexFormat.of().formatHex(s.sign());
        return mvc.perform(post("/v1/terminals/" + terminal + "/staff-signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                        "staffNumber", number, "pin", pin, "timestamp", ts, "signature", sig))));
    }

    private long now() {
        return Instant.now().getEpochSecond();
    }

    // -------------------------------------------------------------------- tests

    @Test
    @DisplayName("an operator provisions a shop and a terminal enrols with the issued code, end to end")
    void provisionsThroughToEnrolment() throws Exception {
        Shop shop = shopWithTerminal("Mama Njeri Mart");
        assertThat(shop.terminalId()).startsWith("TERM-");
        // The plaintext code is never stored.
        Integer leaked = ownerJdbc.queryForObject(
                "SELECT count(*)::int FROM enrolment_code WHERE code_hash IS NULL", Integer.class);
        assertThat(leaked).isZero();
        admin("GET", "/v1/admin/terminals", shop.tenantId(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("another service can look one terminal up with the internal token, and with nothing else")
    void internalTerminalLookup() throws Exception {
        Shop shop = shopWithTerminal("Lookup Lane");
        String path = "/v1/internal/terminals/" + shop.terminalId();
        mvc.perform(get(path).header("Authorization", INTERNAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(shop.tenantId()))
                .andExpect(jsonPath("$.branchId").value(shop.branchId()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.publicKey").value(
                        Base64.getEncoder().encodeToString(shop.key().getPublic().getEncoded())));
        // The operator's admin token is a different credential and does not open this door.
        mvc.perform(get(path).header("Authorization", ADMIN)).andExpect(status().isUnauthorized());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/internal/terminals/TERM-00000000000000000000").header("Authorization", INTERNAL))
                .andExpect(status().isNotFound());
        // Not a way to enumerate: only the exact id shape is routed at all.
        mvc.perform(get("/v1/internal/terminals/").header("Authorization", INTERNAL))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("admin endpoints refuse a missing or wrong token")
    void adminNeedsTheToken() throws Exception {
        mvc.perform(post("/v1/admin/tenants").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/admin/staff").header("Authorization", "Bearer wrong").header("X-Mara-Tenant", "X"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a correct PIN at the tenant's own terminal signs a cashier in and resets the failure count")
    void signsIn() throws Exception {
        Shop shop = shopWithTerminal("Kamau Kiosk");
        addCashier(shop, "2001", "4826", shop.branchId());
        signIn(shop, shop.terminalId(), "2001", "0000", now(), shop.key()).andExpect(status().isUnauthorized());
        signIn(shop, shop.terminalId(), "2001", "4826", now(), shop.key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CASHIER"))
                .andExpect(jsonPath("$.displayName").value("Cashier 2001"));
        Integer failures = ownerJdbc.queryForObject(
                "SELECT failed_attempts FROM staff WHERE staff_number = '2001'", Integer.class);
        assertThat(failures).isZero();
    }

    @Test
    @DisplayName("repeated wrong PINs lock the account, and the lock holds even against the right PIN")
    void locksOut() throws Exception {
        Shop shop = shopWithTerminal("Lockout Lane");
        addCashier(shop, "2002", "4826", shop.branchId());
        for (int i = 0; i < 5; i++) {
            signIn(shop, shop.terminalId(), "2002", "1357", now(), shop.key()).andExpect(status().isUnauthorized());
        }
        signIn(shop, shop.terminalId(), "2002", "4826", now(), shop.key())
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.lockedUntil").exists());
        // The counter survived the refusals: it was committed, not rolled back with them.
        Integer failures = ownerJdbc.queryForObject(
                "SELECT failed_attempts FROM staff WHERE staff_number = '2002'", Integer.class);
        assertThat(failures).isEqualTo(5);
    }

    @Test
    @DisplayName("a signature from any key but the terminal's own is refused, as is a stale request")
    void terminalMustProveItself() throws Exception {
        Shop shop = shopWithTerminal("Forged Foods");
        addCashier(shop, "2003", "4826", shop.branchId());
        KeyPair stranger = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        signIn(shop, shop.terminalId(), "2003", "4826", now(), stranger).andExpect(status().isUnauthorized());
        signIn(shop, shop.terminalId(), "2003", "4826", now() - 3600, shop.key()).andExpect(status().isUnauthorized());
        signIn(shop, "TERM-00000000000000000000", "2003", "4826", now(), shop.key()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a cashier's PIN works at their own branch only")
    void branchBinding() throws Exception {
        Shop shop = shopWithTerminal("Two Branch Traders");
        String other = body(admin("POST", "/v1/admin/branches", shop.tenantId(),
                java.util.Map.of("name", "Westlands", "timezone", "Africa/Nairobi")).andExpect(status().isCreated()))
                .get("branchId").asText();
        addCashier(shop, "2004", "4826", other);
        signIn(shop, shop.terminalId(), "2004", "4826", now(), shop.key()).andExpect(status().isUnauthorized());
        // The owner spans branches, so the owner's PIN works here.
        signIn(shop, shop.terminalId(), "1000", "7391", now(), shop.key()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("one shop's staff number cannot sign in at another shop's terminal")
    void tenantsAreSeparate() throws Exception {
        Shop a = shopWithTerminal("Shop A");
        Shop b = shopWithTerminal("Shop B");
        addCashier(a, "2005", "4826", a.branchId());
        signIn(b, b.terminalId(), "2005", "4826", now(), b.key()).andExpect(status().isUnauthorized());
        admin("GET", "/v1/admin/staff", b.tenantId(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("a suspended cashier cannot sign in, and every outcome lands in an append-only audit log")
    void suspendedAndAudited() throws Exception {
        Shop shop = shopWithTerminal("Audit Alley");
        String id = addCashier(shop, "2006", "4826", shop.branchId());
        admin("POST", "/v1/admin/staff/" + id + "/status", shop.tenantId(), java.util.Map.of("status", "SUSPENDED"))
                .andExpect(status().isNoContent());
        signIn(shop, shop.terminalId(), "2006", "4826", now(), shop.key()).andExpect(status().isUnauthorized());
        admin("GET", "/v1/admin/audit", shop.tenantId(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.action=='staff.signin')]").exists());
        // Even the owner, acting inside the tenant (row-level security is FORCEd on it too),
        // cannot rewrite or remove an audit row.
        for (String sql : new String[] {"UPDATE audit_log SET outcome = 'OK'", "DELETE FROM audit_log"}) {
            assertThatThrownBy(() -> ownerJdbc.execute((java.sql.Connection c) -> {
                try (var st = c.createStatement()) {
                    st.execute("SELECT set_config('mara.tenant_id', '" + shop.tenantId() + "', false)");
                    st.execute(sql);
                }
                return null;
            })).rootCause().hasMessageContaining("append-only");
        }
    }

    @Test
    @DisplayName("PINs are stored as Argon2id and trivial PINs are refused")
    void pinHygiene() throws Exception {
        Shop shop = shopWithTerminal("Hashing House");
        addCashier(shop, "2007", "4826", shop.branchId());
        String stored = ownerJdbc.queryForObject("SELECT pin_hash FROM staff WHERE staff_number = '2007'", String.class);
        assertThat(stored).startsWith("$argon2id$").doesNotContain("4826");
        for (String weak : new String[] {"1234", "0000", "9876", "12"}) {
            admin("POST", "/v1/admin/staff", shop.tenantId(), java.util.Map.of(
                    "branchId", shop.branchId(), "displayName", "Weak", "role", "CASHIER",
                    "staffNumber", "W" + weak, "pin", weak)).andExpect(status().isUnprocessableEntity());
        }
        assertThat(PinHasher.refusalFor("7391")).isNull();
    }

    @Test
    @DisplayName("the admin filter refuses to exist without a real token")
    void noDefaultToken() {
        assertThatThrownBy(() -> new AdminTokenFilter("short")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AdminTokenFilter(null)).isInstanceOf(IllegalStateException.class);
        assertThat(StandardCharsets.UTF_8).isNotNull();
    }
}
