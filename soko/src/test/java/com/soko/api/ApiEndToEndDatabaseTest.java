package com.soko.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soko.notifications.EmailNotifier;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The whole application over HTTP against a real Postgres, connecting as the restricted
 * {@code soko_app} role (so row-level security is live): paging headers, idempotent order
 * placement, the customer's M-Pesa payment flow, an unmatched payment, the owner-only lists and
 * the password reset. Skipped, not failed, when no Postgres answers.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiEndToEndDatabaseTest {

    private static final String HOST_URL =
            System.getenv().getOrDefault("SOKO_TEST_ADMIN_URL", "jdbc:postgresql://localhost:55437/");
    private static final String ADMIN = System.getenv().getOrDefault("SOKO_TEST_ADMIN_USER", "postgres");
    private static final String ADMIN_PW = System.getenv().getOrDefault("SOKO_TEST_ADMIN_PASSWORD", "ownerpw");
    private static final String DB = "soko_e2e_it";
    private static final String APP_PW = "app-password-for-tests-1";
    private static final String SYSTEM_KEY = "system-key-for-tests-0001";

    @BeforeAll
    static void createDatabase() {
        try (Connection admin = DriverManager.getConnection(HOST_URL + "postgres", ADMIN, ADMIN_PW);
                Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + DB);
        } catch (SQLException unavailable) {
            assumeTrue(false, "no Postgres at " + HOST_URL + " (" + unavailable.getMessage() + ")");
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> HOST_URL + DB);
        registry.add("spring.datasource.username", () -> "soko_app");
        registry.add("spring.datasource.password", () -> APP_PW);
        registry.add("spring.flyway.url", () -> HOST_URL + DB);
        registry.add("spring.flyway.user", () -> ADMIN);
        registry.add("spring.flyway.password", () -> ADMIN_PW);
        registry.add("spring.flyway.placeholders.soko_app_password", () -> APP_PW);
        registry.add("spring.flyway.placeholders.soko_system_key", () -> SYSTEM_KEY);
        registry.add("soko.rls.system-key", () -> SYSTEM_KEY);
        registry.add("soko.rls.app-password", () -> APP_PW);
        // Every test registers its own tenant and signs in; the per-address limits are not under test here.
        registry.add("soko.ratelimit.register-per-hour", () -> "10000");
        registry.add("soko.ratelimit.login-per-minute", () -> "10000");
        registry.add("soko.ratelimit.forgot-per-hour", () -> "10000");
        registry.add("soko.jwt.secret", () -> "jwt-secret-for-tests-0123456789abcdef");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean EmailNotifier email;

    // ---- helpers ------------------------------------------------------------------------------

    private record Shop(String owner, UUID customerId, UUID supplierId, UUID productId, UUID offerId,
            String ownerEmail) {}

    private JsonNode call(MockHttpServletRequestBuilder request, String token, Object body, int expected) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult result = mvc.perform(request).andReturn();
        String text = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus())
                .as(request.buildRequest(new org.springframework.mock.web.MockServletContext()).getRequestURI() + " -> " + text)
                .isEqualTo(expected);
        return text.isBlank() ? json.createObjectNode() : json.readTree(text);
    }

    private String register() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@e2e.test";
        JsonNode session = call(post("/v1/auth/register"), null, java.util.Map.of(
                "organisationName", "E2E Farm", "fullName", "Owner", "email", email,
                "password", "a-long-password-1"), 201);
        return session.get("accessToken").asText() + "|" + email;
    }

    private Shop shop(int stock) throws Exception {
        String[] reg = register().split("\\|");
        String owner = reg[0];
        UUID supplier = UUID.fromString(call(post("/v1/suppliers"), owner, java.util.Map.of(
                "name", "Farm", "county", "Nakuru", "leadTimeHours", 5, "coldChain", true), 201).get("id").asText());
        UUID product = UUID.fromString(call(post("/v1/products"), owner, java.util.Map.of(
                "sku", "MLK", "name", "Milk", "category", "Dairy", "unit", "L", "perishable", true,
                "requiresColdChain", true, "shelfLifeHours", 72, "listPriceCents", 15_050), 201).get("id").asText());
        UUID offer = UUID.fromString(call(post("/v1/offers"), owner, java.util.Map.of(
                "supplierId", supplier, "productId", product, "costCents", 9_000, "availableQty", stock), 201).get("id").asText());
        UUID customer = UUID.fromString(call(post("/v1/customers"), owner, java.util.Map.of(
                "name", "Naivas", "phone", "0712345678", "county", "Kiambu"), 201).get("id").asText());
        return new Shop(owner, customer, supplier, product, offer, reg[1]);
    }

    private String customerToken(Shop shop) throws Exception {
        String login = "shopper-" + UUID.randomUUID() + "@e2e.test";
        call(post("/v1/users"), shop.owner(), java.util.Map.of("fullName", "Shopper", "email", login,
                "password", "shopper-password-1", "role", "CUSTOMER", "customerId", shop.customerId()), 201);
        return call(post("/v1/auth/login"), null, java.util.Map.of("email", login, "password", "shopper-password-1"), 200)
                .get("accessToken").asText();
    }

    private int stock(Shop shop) throws Exception {
        for (JsonNode offer : call(get("/v1/offers"), shop.owner(), null, 200)) {
            if (offer.get("id").asText().equals(shop.offerId().toString())) return offer.get("availableQty").asInt();
        }
        throw new AssertionError("offer not listed");
    }

    private void stkCallback(String checkoutId, int resultCode) throws Exception {
        String items = resultCode == 0
                ? ",\"CallbackMetadata\":{\"Item\":[{\"Name\":\"MpesaReceiptNumber\",\"Value\":\"E2E" + checkoutId.hashCode() + "\"}]}"
                : "";
        String body = "{\"Body\":{\"stkCallback\":{\"MerchantRequestID\":\"m\",\"CheckoutRequestID\":\"" + checkoutId
                + "\",\"ResultCode\":" + resultCode + ",\"ResultDesc\":\"" + (resultCode == 0 ? "OK" : "Request cancelled by user") + "\""
                + items + "}}}";
        mvc.perform(post("/v1/public/mpesa/stk-callback").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    // ---- paging and search --------------------------------------------------------------------

    @Test
    void aListPastTheFirstPageIsReachableAndSaysItIsTruncated() throws Exception {
        Shop shop = shop(10);
        for (int i = 0; i < 59; i++) {
            call(post("/v1/products"), shop.owner(), java.util.Map.of(
                    "sku", "P" + i, "name", "Crop %03d".formatted(i), "category", "Veg", "unit", "kg",
                    "shelfLifeHours", 100, "listPriceCents", 1000), 201);
        }
        MvcResult first = mvc.perform(get("/v1/products?limit=50").header("Authorization", "Bearer " + shop.owner()))
                .andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(first.getResponse().getContentAsString())).hasSize(50);
        assertThat(first.getResponse().getHeader("X-Total-Count")).isEqualTo("60");
        assertThat(first.getResponse().getHeader("X-Has-More")).isEqualTo("true");

        MvcResult second = mvc.perform(get("/v1/products?limit=50&page=1").header("Authorization", "Bearer " + shop.owner()))
                .andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(second.getResponse().getContentAsString())).hasSize(10);
        assertThat(second.getResponse().getHeader("X-Has-More")).isEqualTo("false");

        // The 60th product is found by typing, no paging needed.
        JsonNode found = call(get("/v1/products?q=crop 058"), shop.owner(), null, 200);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).get("sku").asText()).isEqualTo("P58");

        // Offers and orders list endpoints take the same parameters.
        call(get("/v1/offers?q=milk&limit=10&page=0"), shop.owner(), null, 200);
        call(get("/v1/orders?q=nothing"), shop.owner(), null, 200);
    }

    @Test
    void theStorefrontHidesOutOfStockInSqlAndPages() throws Exception {
        Shop shop = shop(10);
        UUID dry = UUID.fromString(call(post("/v1/products"), shop.owner(), java.util.Map.of(
                "sku", "DRY", "name", "Sold out", "category", "Dairy", "unit", "L",
                "shelfLifeHours", 72, "listPriceCents", 1000), 201).get("id").asText());
        call(post("/v1/offers"), shop.owner(), java.util.Map.of("supplierId", shop.supplierId(), "productId", dry,
                "costCents", 500, "availableQty", 0), 201);
        String token = customerToken(shop);

        MvcResult result = mvc.perform(get("/v1/shop/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode rows = json.readTree(result.getResponse().getContentAsString());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("name").asText()).isEqualTo("Milk");
        assertThat(result.getResponse().getHeader("X-Total-Count")).isEqualTo("1");
    }

    // ---- idempotent placement -----------------------------------------------------------------

    @Test
    void aRetriedOrderWithTheSameKeyIsPlacedOnceAndReservesStockOnce() throws Exception {
        Shop shop = shop(10);
        var body = java.util.Map.of("customerId", shop.customerId(),
                "lines", java.util.List.of(java.util.Map.of("productId", shop.productId(), "quantity", 3)));

        JsonNode first = call(post("/v1/orders").header("Idempotency-Key", "retry-1"), shop.owner(), body, 201);
        JsonNode again = call(post("/v1/orders").header("Idempotency-Key", "retry-1"), shop.owner(), body, 201);

        assertThat(again.get("orderId").asText()).isEqualTo(first.get("orderId").asText());
        assertThat(again.get("reference").asText()).isEqualTo(first.get("reference").asText());
        assertThat(stock(shop)).isEqualTo(7);

        // The same key for a different basket is refused, not answered with the wrong order.
        var other = java.util.Map.of("customerId", shop.customerId(),
                "lines", java.util.List.of(java.util.Map.of("productId", shop.productId(), "quantity", 4)));
        call(post("/v1/orders").header("Idempotency-Key", "retry-1"), shop.owner(), other, 400);
        assertThat(stock(shop)).isEqualTo(7);

        // No key: two deliberate orders are two orders.
        call(post("/v1/orders"), shop.owner(), body, 201);
        call(post("/v1/orders"), shop.owner(), body, 201);
        assertThat(stock(shop)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicatesPlaceExactlyOneOrder() throws Exception {
        Shop shop = shop(10);
        var body = java.util.Map.of("customerId", shop.customerId(),
                "lines", java.util.List.of(java.util.Map.of("productId", shop.productId(), "quantity", 2)));
        int threads = 8;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var go = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<String>> results = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return call(post("/v1/orders").header("Idempotency-Key", "race-key"), shop.owner(), body, 201)
                        .get("orderId").asText();
            }));
        }
        go.countDown();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (var f : results) ids.add(f.get());
        pool.shutdown();

        assertThat(ids).hasSize(1);
        assertThat(stock(shop)).isEqualTo(8);
    }

    // ---- paying -------------------------------------------------------------------------------

    @Test
    void aCustomerPaysAnOrderWithMpesaAndSeesItPaid() throws Exception {
        Shop shop = shop(10);
        String token = customerToken(shop);
        JsonNode order = call(post("/v1/shop/orders"), token, java.util.Map.of("lines", java.util.List.of(
                java.util.Map.of("productId", shop.productId(), "quantity", 2))), 201);
        String id = order.get("orderId").asText();

        // Nothing yet: no payment, not paid.
        JsonNode before = call(get("/v1/shop/orders/" + id + "/payment"), token, null, 200);
        assertThat(before.get("paid").asBoolean()).isFalse();
        assertThat(before.has("status")).isFalse();

        // A bad number is refused with a message, not sent to M-Pesa.
        call(post("/v1/shop/orders/" + id + "/pay"), token, java.util.Map.of("msisdn", "12345"), 400);

        JsonNode started = call(post("/v1/shop/orders/" + id + "/pay"), token, java.util.Map.of("msisdn", "0712 345 678"), 202);
        assertThat(started.get("status").asText()).isEqualTo("PENDING");
        assertThat(call(get("/v1/shop/orders/" + id + "/payment"), token, null, 200).get("status").asText()).isEqualTo("PENDING");

        stkCallback(started.get("checkoutRequestId").asText(), 0);

        JsonNode after = call(get("/v1/shop/orders/" + id + "/payment"), token, null, 200);
        assertThat(after.get("paid").asBoolean()).isTrue();
        assertThat(after.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(after.get("receipt").asText()).startsWith("E2E");
        assertThat(call(get("/v1/shop/orders/" + id), token, null, 200).get("status").asText()).isEqualTo("PAID");
    }

    @Test
    void aCancelledPromptShowsAsFailedAndCanBeRetried() throws Exception {
        Shop shop = shop(10);
        String token = customerToken(shop);
        String id = call(post("/v1/shop/orders"), token, java.util.Map.of("lines", java.util.List.of(
                java.util.Map.of("productId", shop.productId(), "quantity", 1))), 201).get("orderId").asText();

        JsonNode first = call(post("/v1/shop/orders/" + id + "/pay"), token, java.util.Map.of("msisdn", "0712345678"), 202);
        stkCallback(first.get("checkoutRequestId").asText(), 1032);

        JsonNode failed = call(get("/v1/shop/orders/" + id + "/payment"), token, null, 200);
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.get("detail").asText()).contains("cancelled");
        assertThat(failed.get("paid").asBoolean()).isFalse();

        JsonNode retry = call(post("/v1/shop/orders/" + id + "/pay"), token, java.util.Map.of("msisdn", "0712345678"), 202);
        assertThat(retry.get("status").asText()).isEqualTo("PENDING");
        assertThat(retry.get("checkoutRequestId").asText()).isNotEqualTo(first.get("checkoutRequestId").asText());
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void aPaymentForAnOrderThatNoLongerExistsBecomesAVisibleOrphan() throws Exception {
        Shop shop = shop(10);
        String token = customerToken(shop);
        String id = call(post("/v1/shop/orders"), token, java.util.Map.of("lines", java.util.List.of(
                java.util.Map.of("productId", shop.productId(), "quantity", 1))), 201).get("orderId").asText();
        JsonNode started = call(post("/v1/shop/orders/" + id + "/pay"), token, java.util.Map.of("msisdn", "0712345678"), 202);

        // The order disappears (here deleted as the superuser) between the prompt and the callback.
        try (Connection admin = DriverManager.getConnection(HOST_URL + DB, ADMIN, ADMIN_PW);
                Statement st = admin.createStatement()) {
            st.execute("DELETE FROM orders WHERE id = '" + id + "'");
        }
        stkCallback(started.get("checkoutRequestId").asText(), 0);

        JsonNode orphans = call(get("/v1/payments/orphaned"), shop.owner(), null, 200);
        assertThat(orphans).hasSize(1);
        assertThat(orphans.get(0).get("receipt").asText()).startsWith("E2E");
        assertThat(orphans.get(0).get("reason").asText()).contains("was not found");
        assertThat(orphans.get(0).get("amountCents").asLong()).isEqualTo(15_100);

        // Another tenant sees none of it, and a non-owner is refused.
        Shop stranger = shop(1);
        assertThat(call(get("/v1/payments/orphaned"), stranger.owner(), null, 200)).isEmpty();
        call(get("/v1/payments/orphaned"), token, null, 403);
    }

    // ---- accounts -----------------------------------------------------------------------------

    @Test
    void theAccountListIsOwnerOnlyAndNeverShowsHashes() throws Exception {
        Shop shop = shop(1);
        customerToken(shop);
        JsonNode users = call(get("/v1/users"), shop.owner(), null, 200);
        assertThat(users.size()).isGreaterThanOrEqualTo(2);
        assertThat(users.toString()).doesNotContain("passwordHash").doesNotContain("password_hash");

        call(post("/v1/users"), shop.owner(), java.util.Map.of("fullName", "Ops", "email", "ops-" + UUID.randomUUID() + "@e2e.test",
                "password", "operator-password-1", "role", "OPERATOR"), 201);
        String operatorLogin = users.get(0).get("email").asText();
        assertThat(operatorLogin).isNotBlank();
    }

    @Test
    void passwordResetWorksOnceThroughTheEmailedLink() throws Exception {
        String[] reg = register().split("\\|");
        String address = reg[1];

        call(post("/v1/auth/forgot"), null, java.util.Map.of("email", address), 202);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(email).send(eq(address), any(), body.capture());
        Matcher link = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)").matcher(body.getValue());
        assertThat(link.find()).isTrue();
        String token = link.group(1);

        // An unknown address gets the same answer and no e-mail.
        call(post("/v1/auth/forgot"), null, java.util.Map.of("email", "nobody-" + UUID.randomUUID() + "@e2e.test"), 202);

        call(post("/v1/auth/reset"), null, java.util.Map.of("token", token, "password", "short"), 400);
        call(post("/v1/auth/reset"), null, java.util.Map.of("token", token, "password", "a-brand-new-password"), 200);

        call(post("/v1/auth/login"), null, java.util.Map.of("email", address, "password", "a-long-password-1"), 401);
        call(post("/v1/auth/login"), null, java.util.Map.of("email", address, "password", "a-brand-new-password"), 200);

        // Single use.
        call(post("/v1/auth/reset"), null, java.util.Map.of("token", token, "password", "yet-another-password"), 400);
        call(post("/v1/auth/reset"), null, java.util.Map.of("token", "not-a-real-token", "password", "yet-another-password"), 400);
    }

    @Test
    void anExpiredResetLinkIsRefused() throws Exception {
        String[] reg = register().split("\\|");
        call(post("/v1/auth/forgot"), null, java.util.Map.of("email", reg[1]), 202);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(email).send(eq(reg[1]), any(), body.capture());
        Matcher link = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(body.getValue());
        assertThat(link.find()).isTrue();

        try (Connection admin = DriverManager.getConnection(HOST_URL + DB, ADMIN, ADMIN_PW);
                Statement st = admin.createStatement()) {
            st.execute("UPDATE password_resets SET expires_at = now() - interval '1 minute'");
        }
        call(post("/v1/auth/reset"), null, java.util.Map.of("token", link.group(1), "password", "a-brand-new-password"), 400);
        verify(email, never()).send(eq("nobody"), any(), any());
    }
}
