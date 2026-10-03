package com.hms.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Runs the real application against a real Postgres, connected as the least-privilege application
 * role exactly as in production, so row-level security is genuinely in force. Point it at a database
 * with HMS_TEST_DB_URL (default: a local Postgres on 55436 started by the test instructions).
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    protected static final String DB_URL = System.getenv().getOrDefault("HMS_TEST_DB_URL", "jdbc:postgresql://localhost:55436/hms_test");
    protected static final String OWNER = "hms";
    protected static final String OWNER_PASSWORD = "ownerpw";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> DB_URL);
        r.add("spring.datasource.username", () -> "hms_app");
        r.add("spring.datasource.password", () -> "apppw-test");
        r.add("spring.flyway.url", () -> DB_URL);
        r.add("spring.flyway.user", () -> OWNER);
        r.add("spring.flyway.password", () -> OWNER_PASSWORD);
        r.add("spring.flyway.placeholders.appUser", () -> "hms_app");
        r.add("spring.flyway.placeholders.appPassword", () -> "apppw-test");
        r.add("hms.jwt.secret", () -> "a-test-signing-secret-that-is-long-enough-32-chars");
        r.add("hms.security.login-per-minute", () -> "10000");
        r.add("hms.security.onboarding-per-hour", () -> "10000");
        r.add("hms.roles.cache-ttl-ms", () -> "0");
        // Tests run the dispatcher by hand so they can see each pass.
        r.add("hms.notifications.dispatcher", () -> "false");
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    protected record Org(UUID orgId, UUID facilityId, UUID adminId, String email, String password, String token) {}

    @BeforeAll
    static void migrateOnce() {
        // Spring runs Flyway at context start; nothing else to do. Kept for subclasses that need ordering.
    }

    /** Creates an organisation through the public endpoint and signs its administrator in. */
    protected Org newOrg(String slugPrefix) throws Exception {
        String slug = slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        String email = slug + "@example.org";
        String password = "correct-horse-battery";
        MvcResult created = mvc.perform(post("/v1/organisations", null).content(json.writeValueAsString(Map.of(
                        "organisationName", "Org " + slug, "slug", slug,
                        "facility", Map.of("name", "Main Hospital", "mflCode", "10" + (int) (Math.random() * 900 + 100), "kephLevel", 4, "ownership", "PRIVATE", "county", "Nairobi"),
                        "admin", Map.of("fullName", "Admin " + slug, "email", email, "password", password)))))
                .andReturn();
        if (created.getResponse().getStatus() != 201) {
            throw new IllegalStateException("onboarding failed: " + created.getResponse().getContentAsString());
        }
        JsonNode body = json.readTree(created.getResponse().getContentAsString());
        String token = login(email, password);
        return new Org(UUID.fromString(body.get("organisationId").asText()), UUID.fromString(body.get("facilityId").asText()),
                UUID.fromString(body.get("practitionerId").asText()), email, password, token);
    }

    protected String login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/v1/auth/login", null).content(json.writeValueAsString(Map.of("email", email, "password", password)))).andReturn();
        if (r.getResponse().getStatus() != 200) {
            throw new IllegalStateException("login failed: " + r.getResponse().getStatus() + " " + r.getResponse().getContentAsString());
        }
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    protected MockHttpServletRequestBuilder post(String url, String token) {
        var b = MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON);
        return token == null ? b : b.header("Authorization", "Bearer " + token);
    }

    protected MockHttpServletRequestBuilder get(String url, String token) {
        return MockMvcRequestBuilders.get(url).header("Authorization", "Bearer " + token);
    }

    protected MockHttpServletRequestBuilder put(String url, String token) {
        return MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token);
    }

    /** Runs SQL as the schema OWNER, outside every application rule, to set up or tamper with data. */
    protected void asOwner(String... statements) throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, OWNER, OWNER_PASSWORD); Statement s = c.createStatement()) {
            for (String sql : statements) {
                s.execute(sql);
            }
        }
    }

    protected Map<String, Object> patient(UUID facilityId, String given, String family, String birth, Object... extra) {
        Map<String, Object> demographics = new java.util.LinkedHashMap<>(Map.of("givenName", given, "familyName", family, "sex", "FEMALE", "birthDate", birth));
        for (int i = 0; i + 1 < extra.length; i += 2) {
            demographics.put((String) extra[i], extra[i + 1]);
        }
        return new java.util.LinkedHashMap<>(Map.of("facilityId", facilityId.toString(), "demographics", demographics));
    }

    // ---- terse helpers used by the module tests ---------------------------------------------

    protected MockHttpServletRequestBuilder delete(String url, String token) {
        return MockMvcRequestBuilders.delete(url).header("Authorization", "Bearer " + token);
    }

    /** Sends the request, asserts the HTTP status, and returns the JSON body (or null when empty). */
    protected JsonNode send(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult r = mvc.perform(request).andReturn();
        String text = r.getResponse().getContentAsString();
        if (r.getResponse().getStatus() != expectedStatus) {
            throw new AssertionError("expected HTTP " + expectedStatus + " but got " + r.getResponse().getStatus() + ": " + text);
        }
        return text.isEmpty() ? null : json.readTree(text);
    }

    protected JsonNode sendJson(MockHttpServletRequestBuilder request, Object body, int expectedStatus) throws Exception {
        return send(request.content(json.writeValueAsString(body)), expectedStatus);
    }

    /** POST with a JSON body, expecting 201 (or the given status), returning the JSON. */
    protected JsonNode create(String url, String token, Object body) throws Exception {
        return sendJson(post(url, token), body, 201);
    }

    protected JsonNode fetch(String url, String token) throws Exception {
        return send(get(url, token), 200);
    }

    /** Creates a staff member holding one standard role at the org's facility and returns their token. */
    protected String userWithRole(Org org, String roleKey) throws Exception {
        return userWithRole(org, roleKey, "DOCTOR");
    }

    protected String userWithRole(Org org, String roleKey, String cadre) throws Exception {
        String email = roleKey.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
        create("/v1/staff", org.token(), Map.of("email", email, "fullName", "Test " + roleKey, "cadre", cadre,
                "licenceBody", "KMPDC", "licenceNo", "A" + (int) (Math.random() * 90000 + 10000),
                "temporaryPassword", "temporary-password-1", "roles", java.util.List.of(roleKey), "facilityIds", java.util.List.of(org.facilityId())));
        return login(email, "temporary-password-1");
    }

    protected UUID practitionerIdOf(String token) throws Exception {
        return UUID.fromString(fetch("/v1/auth/me", token).get("practitionerId").asText());
    }
}
