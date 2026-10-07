package com.hms.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.platform.web.ApiException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Safaricom Daraja "Lipa na M-Pesa Online" (STK push). WRITTEN FROM THE PUBLIC DARAJA DOCUMENTATION AND NEVER RUN AGAINST
 * SAFARICOM: it is tested only against a stub server that imitates the documented shapes. Before going live check, with your own
 * sandbox credentials, the field names, the callback body and the callback source addresses against Safaricom's current documentation.
 */
public class DarajaMpesaGateway implements MpesaGateway {

    public record Config(String baseUrl, String consumerKey, String consumerSecret, String shortcode, String passkey, String callbackUrl) {}

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Config config;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private volatile String token;
    private volatile long tokenExpiresAt;

    public DarajaMpesaGateway(Config config) {
        this.config = config;
    }

    @Override
    public StkRequest initiate(String phoneE164, BigDecimal amount, String accountReference, String description) {
        String stamp = ZonedDateTime.now(ZoneId.of("Africa/Nairobi")).format(STAMP);
        String password = Base64.getEncoder().encodeToString((config.shortcode() + config.passkey() + stamp).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("BusinessShortCode", config.shortcode());
        body.put("Password", password);
        body.put("Timestamp", stamp);
        body.put("TransactionType", "CustomerPayBillOnline");
        // Daraja wants whole shillings.
        body.put("Amount", amount.setScale(0, java.math.RoundingMode.CEILING).toPlainString());
        String msisdn = phoneE164.startsWith("+") ? phoneE164.substring(1) : phoneE164;
        body.put("PartyA", msisdn);
        body.put("PartyB", config.shortcode());
        body.put("PhoneNumber", msisdn);
        body.put("CallBackURL", config.callbackUrl());
        body.put("AccountReference", truncate(accountReference, 12));
        body.put("TransactionDesc", truncate(description, 13));
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(config.baseUrl() + "/mpesa/stkpush/v1/processrequest"))
                    .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + accessToken()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 401) {
                token = null;
            }
            JsonNode n = r.body() == null || r.body().isBlank() ? json.createObjectNode() : json.readTree(r.body());
            if (r.statusCode() / 100 != 2 || !"0".equals(n.path("ResponseCode").asText()) || n.path("CheckoutRequestID").asText("").isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "mpesa_rejected", "M-Pesa did not accept the request (" + r.statusCode() + "): "
                        + truncate(n.path("errorMessage").asText(n.path("ResponseDescription").asText("no detail")), 120));
            }
            return new StkRequest(n.get("CheckoutRequestID").asText(), n.path("CustomerMessage").asText("Prompt sent to the customer's phone."));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "mpesa_unreachable", "M-Pesa could not be reached; nothing was requested. Try again.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "mpesa_unreachable", "M-Pesa could not be reached; nothing was requested. Try again.");
        }
    }

    private String accessToken() throws IOException, InterruptedException {
        String cached = token;
        if (cached != null && System.currentTimeMillis() < tokenExpiresAt) {
            return cached;
        }
        String basic = Base64.getEncoder().encodeToString((config.consumerKey() + ":" + config.consumerSecret()).getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(config.baseUrl() + "/oauth/v1/generate?grant_type=client_credentials"))
                .timeout(Duration.ofSeconds(15)).header("Authorization", "Basic " + basic).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "mpesa_auth_failed", "M-Pesa refused the configured credentials (" + r.statusCode() + ").");
        }
        JsonNode n = json.readTree(r.body());
        String fresh = n.path("access_token").asText("");
        if (fresh.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "mpesa_auth_failed", "M-Pesa returned no access token.");
        }
        long seconds = Math.max(30, n.path("expires_in").asLong(3599) - 60);
        token = fresh;
        tokenExpiresAt = System.currentTimeMillis() + seconds * 1000;
        return fresh;
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max);
    }

    @Override
    public boolean isMock() {
        return false;
    }
}
