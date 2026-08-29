package com.kenyarealestate.viewing.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class PaymentClient {

    private final RestTemplate rt;

    @Value("${services.payment-url:http://payment-service:8085}")
    private String paymentUrl;

    @Value("${services.internal-secret}")
    private String internalSecret;

    public PaymentClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.rt = new RestTemplate(factory);
    }

    @Data
    public static class PaymentInitResult {
        private UUID id;
        private String status;
        private String mpesaCheckoutRequestId;
    }

    @CircuitBreaker(name = "payment-service", fallbackMethod = "initiateViewingFeeFallback")
    @Retry(name = "payment-service", fallbackMethod = "initiateViewingFeeFallback")
    public PaymentInitResult initiateViewingFee(UUID buyerId, UUID sellerId,
                                                  UUID propertyId, UUID viewingId,
                                                  String buyerPhone) {
        Map<String, Object> body = Map.of(
                "propertyId",     propertyId.toString(),
                "sellerId",       sellerId.toString(),
                "amount",         200,
                "phoneNumber",    buyerPhone,
                "paymentType",    "VIEWING_FEE",
                "idempotencyKey", "vf:" + viewingId.toString()
        );

        HttpHeaders h = new HttpHeaders();
        h.set("X-Auth-UserId",     buyerId.toString());
        h.set("X-Auth-Role",       "BUYER");
        h.set("X-Internal-Secret", internalSecret);
        h.setContentType(MediaType.APPLICATION_JSON);

        var resp = rt.exchange(
                paymentUrl + "/api/payments/initiate",
                HttpMethod.POST,
                new HttpEntity<>(body, h),
                PaymentInitResult.class);

        return resp.getBody();
    }

    @SuppressWarnings("unused")
    private PaymentInitResult initiateViewingFeeFallback(UUID buyerId, UUID sellerId,
                                                           UUID propertyId, UUID viewingId,
                                                           String buyerPhone, Exception e) {
        log.error("Failed to initiate viewing fee for viewing {}: {}", viewingId, e.getMessage());
        return null;
    }

    @CircuitBreaker(name = "payment-service", fallbackMethod = "refundViewingFeeFallback")
    @Retry(name = "payment-service", fallbackMethod = "refundViewingFeeFallback")
    public boolean refundViewingFee(UUID paymentId, String reason) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Internal-Secret", internalSecret);

        String url = paymentUrl + "/api/payments/internal/" + paymentId
                + "/refund-viewing-fee?reason=" + java.net.URLEncoder.encode(reason, java.nio.charset.StandardCharsets.UTF_8);

        rt.exchange(url, HttpMethod.PUT, new HttpEntity<>(h), Void.class);
        return true;
    }

    @SuppressWarnings("unused")
    private boolean refundViewingFeeFallback(UUID paymentId, String reason, Exception e) {
        log.error("Failed to refund viewing fee for payment {}: {}", paymentId, e.getMessage());
        return false;
    }
}
