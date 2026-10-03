package com.soko.mpesa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real Daraja calls -- only active when {@code soko.mpesa.mode=live}, which
 * needs real sandbox or production credentials. Not wired to anything by
 * default; {@link MockMpesaGateway} is what demos and tests exercise.
 */
@Component
@ConditionalOnProperty(name = "soko.mpesa.mode", havingValue = "live")
public class DarajaMpesaGateway implements MpesaGateway {

    private static final Logger log = LoggerFactory.getLogger(DarajaMpesaGateway.class);
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RestClient http;
    private final MpesaProperties properties;
    private final ObjectMapper objectMapper;

    private String cachedToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public DarajaMpesaGateway(MpesaProperties properties, ObjectMapper objectMapper) {
        // Fail closed: with no secret every caller on the internet could mark an order paid.
        if (properties.callbackSecret() == null || properties.callbackSecret().length() < 16) {
            throw new IllegalStateException(
                    "soko.mpesa.mode=live needs soko.mpesa.callback-secret of at least 16 characters");
        }
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.http = RestClient.builder().baseUrl(properties.baseUrl()).build();
    }

    @Override
    public StkPushResponse stkPush(StkPushRequest request) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP);
        String password = Base64.getEncoder().encodeToString(
                (properties.shortCode() + properties.passkey() + timestamp)
                        .getBytes(StandardCharsets.UTF_8));

        Map<String, Object> body = Map.ofEntries(
                Map.entry("BusinessShortCode", properties.shortCode()),
                Map.entry("Password", password),
                Map.entry("Timestamp", timestamp),
                Map.entry("TransactionType", "CustomerPayBillOnline"),
                Map.entry("Amount", request.amount().toBigInteger()),
                Map.entry("PartyA", request.phoneNumber()),
                Map.entry("PartyB", properties.shortCode()),
                Map.entry("PhoneNumber", request.phoneNumber()),
                Map.entry("CallBackURL", withSecret(request.callbackUrl())),
                Map.entry("AccountReference", request.accountReference()),
                Map.entry("TransactionDesc", request.description()));

        JsonNode response = post("/mpesa/stkpush/v1/processrequest", body);

        String code = text(response, "ResponseCode");
        return new StkPushResponse(
                "0".equals(code),
                text(response, "MerchantRequestID"),
                text(response, "CheckoutRequestID"),
                code,
                text(response, "ResponseDescription"),
                text(response, "CustomerMessage"));
    }

    @Override
    public boolean verifyCallbackSignature(String rawBody, String signature) {
        // Constant-time, and no "blank secret means accept everything" escape hatch.
        return signature != null && java.security.MessageDigest.isEqual(
                properties.callbackSecret().getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    /** The callback URL we register carries the secret, because that URL is the one thing we control. */
    String withSecret(String url) {
        String encoded = java.net.URLEncoder.encode(properties.callbackSecret(), StandardCharsets.UTF_8);
        return url + (url.contains("?") ? "&" : "?") + "secret=" + encoded;
    }

    private JsonNode post(String path, Map<String, Object> body) {
        try {
            String raw = http.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw == null ? "{}" : raw);
        } catch (Exception ex) {
            log.error("M-Pesa call to {} failed", path, ex);
            throw new MpesaException("M-Pesa request failed: " + ex.getMessage(), ex);
        }
    }

    private synchronized String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }

        String basic = Base64.getEncoder().encodeToString(
                (properties.consumerKey() + ":" + properties.consumerSecret())
                        .getBytes(StandardCharsets.UTF_8));
        try {
            String raw = http.get()
                    .uri("/oauth/v1/generate?grant_type=client_credentials")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(raw == null ? "{}" : raw);
            cachedToken = text(node, "access_token");
            long expiresIn = node.path("expires_in").asLong(3599);
            tokenExpiresAt = Instant.now().plus(Duration.ofSeconds(Math.max(60, expiresIn - 60)));
            return cachedToken;
        } catch (Exception ex) {
            throw new MpesaException("Could not obtain an M-Pesa access token", ex);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
