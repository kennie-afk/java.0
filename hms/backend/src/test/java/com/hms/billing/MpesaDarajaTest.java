package com.hms.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.platform.config.ProductionGuard;
import com.hms.platform.config.Redact;
import com.hms.support.IntegrationTest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The Daraja gateway and callback against a STUB that imitates the documented shapes. Never run against Safaricom. */
class MpesaDarajaTest extends IntegrationTest {

    static final String SECRET = "a-callback-secret-of-sufficient-length-0123456789";
    static final List<String> seen = new CopyOnWriteArrayList<>();
    static final HttpServer daraja = start();

    static HttpServer start() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/oauth/v1/generate", ex -> {
                seen.add("GET " + ex.getRequestURI() + " auth=" + ex.getRequestHeaders().getFirst("Authorization"));
                byte[] out = "{\"access_token\":\"tok-123\",\"expires_in\":\"3599\"}".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, out.length);
                ex.getResponseBody().write(out);
                ex.close();
            });
            s.createContext("/mpesa/stkpush/v1/processrequest", ex -> {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                seen.add("POST auth=" + ex.getRequestHeaders().getFirst("Authorization") + " " + body);
                byte[] out = ("{\"MerchantRequestID\":\"m-1\",\"CheckoutRequestID\":\"ws_CO_" + UUID.randomUUID().toString().replace("-", "")
                        + "\",\"ResponseCode\":\"0\",\"ResponseDescription\":\"Success\",\"CustomerMessage\":\"Success. Request accepted for processing\"}").getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, out.length);
                ex.getResponseBody().write(out);
                ex.close();
            });
            s.start();
            return s;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void daraja(DynamicPropertyRegistry r) {
        r.add("hms.mpesa.mode", () -> "daraja");
        r.add("hms.mpesa.base-url", () -> "http://127.0.0.1:" + daraja.getAddress().getPort());
        r.add("hms.mpesa.consumer-key", () -> "ck");
        r.add("hms.mpesa.consumer-secret", () -> "cs");
        r.add("hms.mpesa.shortcode", () -> "174379");
        r.add("hms.mpesa.passkey", () -> "pk");
        r.add("hms.mpesa.callback-base-url", () -> "https://hms.example.org");
        r.add("hms.mpesa.callback-secret", () -> SECRET);
    }

    private String callbackUrl(String secret) {
        return "/v1/billing/mpesa/" + secret + "/confirmation";
    }

    private String stkBody(String checkout, int code, String receipt, Object amount) {
        String meta = code == 0 ? ",\"CallbackMetadata\":{\"Item\":[{\"Name\":\"Amount\",\"Value\":" + amount + "},{\"Name\":\"MpesaReceiptNumber\",\"Value\":\""
                + receipt + "\"},{\"Name\":\"PhoneNumber\",\"Value\":254712345678}]}" : "";
        return "{\"Body\":{\"stkCallback\":{\"MerchantRequestID\":\"m-1\",\"CheckoutRequestID\":\"" + checkout + "\",\"ResultCode\":" + code
                + ",\"ResultDesc\":\"" + (code == 0 ? "The service request is processed successfully." : "Request cancelled by user") + "\"" + meta + "}}}";
    }

    private int callback(String secret, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(callbackUrl(secret))
                .contentType("application/json").content(body)).andReturn().getResponse().getStatus();
    }

    private String invoice(Org org, String cashier, int total) throws Exception {
        UUID patient = UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Mp", "Esa" + UUID.randomUUID().toString().substring(0, 6), "1980-01-01")).get("id").asText());
        String id = create("/v1/billing/invoices", cashier, Map.of("facilityId", org.facilityId().toString(), "patientId", patient.toString())).get("id").asText();
        sendJson(post("/v1/billing/invoices/" + id + "/lines", cashier), Map.of("description", "Consultation", "unitPrice", total, "quantity", 1), 200);
        send(post("/v1/billing/invoices/" + id + "/issue", cashier), 200);
        return id;
    }

    private JsonNode stk(String cashier, String invoice, int amount) throws Exception {
        return sendJson(post("/v1/billing/invoices/" + invoice + "/mpesa", cashier),
                Map.of("phone", "0712 345 678", "amount", amount, "idempotencyKey", "stk-" + UUID.randomUUID()), 202);
    }

    private String scalar(String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, OWNER, OWNER_PASSWORD); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void stkPushUsesTheDarajaShapesAndTheCallbackSettlesOnce() throws Exception {
        Org org = newOrg("daraja");
        String cashier = userWithRole(org, "CASHIER", "ACCOUNTANT");
        String inv = invoice(org, cashier, 1000);
        seen.clear();
        JsonNode req = stk(cashier, inv, 400);
        String checkout = req.get("payment").get("mpesaCheckoutId").asText();
        assertThat(checkout).startsWith("ws_CO_");
        assertThat(req.get("note").asText()).doesNotContain("simulated");
        // Token fetched with Basic credentials, STK sent with the bearer token and the secret-bearing callback URL.
        assertThat(seen.get(0)).contains("grant_type=client_credentials").contains("Basic " + java.util.Base64.getEncoder().encodeToString("ck:cs".getBytes()));
        String post = seen.stream().filter(s -> s.startsWith("POST")).findFirst().orElseThrow();
        assertThat(post).contains("Bearer tok-123").contains("\"BusinessShortCode\":\"174379\"").contains("\"PhoneNumber\":\"254712345678\"")
                .contains("\"Amount\":\"400\"").contains("CallBackURL\":\"https://hms.example.org/v1/billing/mpesa/" + SECRET + "/confirmation");

        // A wrong secret is indistinguishable from no route; nothing is applied.
        assertThat(callback("wrong-" + SECRET, stkBody(checkout, 0, "RCP1" + checkout.substring(6, 12), 400))).isEqualTo(404);
        assertThat(send(get("/v1/billing/invoices/" + inv, cashier), 200).get("amountPaid").decimalValue()).isEqualByComparingTo("0");

        String receipt = "RCP" + UUID.randomUUID().toString().substring(0, 7).toUpperCase();
        assertThat(callback(SECRET, stkBody(checkout, 0, receipt, 400))).isEqualTo(200);
        assertThat(callback(SECRET, stkBody(checkout, 0, receipt, 400))).isEqualTo(200); // Safaricom retries: still one payment, one receipt
        JsonNode after = send(get("/v1/billing/invoices/" + inv, cashier), 200);
        assertThat(after.get("amountPaid").decimalValue()).isEqualByComparingTo("400");
        assertThat(scalar("SELECT count(*) FROM receipts r JOIN payments p ON p.id = r.payment_id WHERE p.mpesa_checkout_id = '" + checkout + "'")).isEqualTo("1");
        assertThat(scalar("SELECT status || ':' || mpesa_receipt FROM payments WHERE mpesa_checkout_id = '" + checkout + "'")).isEqualTo("COMPLETED:" + receipt);
        // The simulated endpoint is not available once a real gateway is configured.
        sendJson(post("/v1/billing/mpesa/mock/complete", cashier), Map.of("checkoutRequestId", checkout, "success", true), 501);
    }

    @Test
    void cancelledPromptFailsThePaymentAndMoneyThatCannotBeApplied() throws Exception {
        Org org = newOrg("daraja2");
        String cashier = userWithRole(org, "CASHIER", "ACCOUNTANT");
        String inv = invoice(org, cashier, 1000);
        String cancelled = stk(cashier, inv, 300).get("payment").get("mpesaCheckoutId").asText();
        assertThat(callback(SECRET, stkBody(cancelled, 1032, null, 0))).isEqualTo(200);
        assertThat(scalar("SELECT status FROM payments WHERE mpesa_checkout_id = '" + cancelled + "'")).isEqualTo("FAILED");

        // Paid an amount other than the one requested: kept for a person to reconcile, payment stays pending, Safaricom still answered 200.
        String mismatch = stk(cashier, inv, 300).get("payment").get("mpesaCheckoutId").asText();
        assertThat(callback(SECRET, stkBody(mismatch, 0, "MIS" + UUID.randomUUID().toString().substring(0, 7).toUpperCase(), 250))).isEqualTo(200);
        assertThat(scalar("SELECT status FROM payments WHERE mpesa_checkout_id = '" + mismatch + "'")).isEqualTo("PENDING");
        assertThat(scalar("SELECT reason || ':' || amount FROM mpesa_unclaimed WHERE checkout_request_id = '" + mismatch + "'")).isEqualTo("AMOUNT_MISMATCH:250.00");

        // A reference we never issued: kept, once, however often it is retried.
        String ghost = "ws_CO_GHOST" + UUID.randomUUID().toString().substring(0, 8);
        String ghostReceipt = "GHO" + UUID.randomUUID().toString().substring(0, 7).toUpperCase();
        assertThat(callback(SECRET, stkBody(ghost, 0, ghostReceipt, 77))).isEqualTo(200);
        assertThat(callback(SECRET, stkBody(ghost, 0, ghostReceipt, 77))).isEqualTo(200);
        assertThat(scalar("SELECT count(*) FROM mpesa_unclaimed WHERE checkout_request_id = '" + ghost + "'")).isEqualTo("1");
        assertThat(scalar("SELECT reason FROM mpesa_unclaimed WHERE checkout_request_id = '" + ghost + "'")).isEqualTo("UNKNOWN_REFERENCE");

        // The application role may add to the table but never change or remove what is in it.
        try (Connection c = DriverManager.getConnection(DB_URL, "hms_app", "apppw-test"); Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.execute("DELETE FROM mpesa_unclaimed")).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> s.execute("UPDATE mpesa_unclaimed SET amount = 1")).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
        }
    }

    @Test
    void malformedInputIsAcknowledgedButATransientFailureIsNot() throws Exception {
        assertThat(callback(SECRET, "not json")).isEqualTo(200);
        assertThat(callback(SECRET, "{\"Body\":{}}")).isEqualTo(200);
        assertThat(callback(SECRET, "{\"Body\":{\"stkCallback\":{\"ResultCode\":0}}}")).isEqualTo(200);
        assertThat(scalar("SELECT count(*) FROM mpesa_unclaimed WHERE checkout_request_id IS NULL")).isEqualTo("0");

        // The lookup is down (stand-in for an outage): the answer must be a 5xx so that Safaricom retries, not an acknowledgement.
        asOwner("ALTER FUNCTION mpesa_find_payment(text) RENAME TO mpesa_find_payment_off");
        try {
            assertThat(callback(SECRET, stkBody("ws_CO_TRANSIENT", 0, "TRN1234567", 10))).isEqualTo(500);
        } finally {
            asOwner("ALTER FUNCTION mpesa_find_payment_off(text) RENAME TO mpesa_find_payment");
        }
        assertThat(scalar("SELECT count(*) FROM mpesa_unclaimed WHERE checkout_request_id = 'ws_CO_TRANSIENT'")).isEqualTo("0");
    }

    @Test
    void theSecretNeverAppearsInLoggedPaths() {
        assertThat(Redact.path(callbackUrl(SECRET))).isEqualTo("/v1/billing/mpesa/***/confirmation").doesNotContain(SECRET);
        assertThat(Redact.path("/v1/billing/invoices")).isEqualTo("/v1/billing/invoices");
    }

    @Test
    void productionRefusesTheMockUnlessExplicitlyAllowed() {
        assertThatThrownBy(() -> ProductionGuard.verify("production", "mock", false)).isInstanceOf(IllegalStateException.class).hasMessageContaining("mock M-Pesa");
        ProductionGuard.verify("production", "mock", true);
        ProductionGuard.verify("production", "daraja", false);
        ProductionGuard.verify("development", "mock", false);
    }
}
