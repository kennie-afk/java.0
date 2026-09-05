package com.smartseason.payment.mpesa;

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

@Component
@ConditionalOnProperty(name = "smartseason.mpesa.mode", havingValue = "live")
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
                Map.entry("CallBackURL", request.callbackUrl()),
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
    public B2cResponse businessToCustomer(B2cRequest request) {
        Map<String, Object> body = Map.ofEntries(
                Map.entry("InitiatorName", properties.initiatorName()),
                Map.entry("SecurityCredential", properties.securityCredential()),
                Map.entry("CommandID", "BusinessPayment"),
                Map.entry("Amount", request.amount().toBigInteger()),
                Map.entry("PartyA", properties.shortCode()),
                Map.entry("PartyB", request.phoneNumber()),
                Map.entry("Remarks", request.remarks()),
                Map.entry("QueueTimeOutURL", request.timeoutUrl()),
                Map.entry("ResultURL", request.resultUrl()),
                Map.entry("Occasion", request.occasion() == null ? "" : request.occasion()));

        JsonNode response = post("/mpesa/b2c/v1/paymentrequest", body);

        String code = text(response, "ResponseCode");
        return new B2cResponse(
                "0".equals(code),
                text(response, "ConversationID"),
                text(response, "OriginatorConversationID"),
                code,
                text(response, "ResponseDescription"));
    }

    @Override
    public boolean verifyCallbackSignature(String rawBody, String signature) {
        return properties.callbackSecret() == null
                || properties.callbackSecret().isBlank()
                || properties.callbackSecret().equals(signature);
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
