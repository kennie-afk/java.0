package com.kenyarealestate.pms.client;

import com.kenyarealestate.pms.dto.InitiatedPaymentResponse;
import com.kenyarealestate.pms.exception.ConflictException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class PaymentServiceClient {

    private final RestTemplate restTemplate;
    private final String paymentUrl;

    public PaymentServiceClient(RestTemplate restTemplate,
                                @Value("${services.payment-url}") String paymentUrl) {
        this.restTemplate = restTemplate;
        this.paymentUrl = paymentUrl;
    }

    @CircuitBreaker(name = "payment-service", fallbackMethod = "unavailable")
    public InitiatedPaymentResponse initiateRentStkPush(String bearerToken, UUID propertyId, UUID landlordId,
                                                        BigDecimal amount, String phoneNumber, UUID invoiceId) {
        Map<String, Object> body = new HashMap<>();
        body.put("propertyId", propertyId.toString());
        body.put("sellerId", landlordId.toString());
        body.put("amount", amount);
        body.put("phoneNumber", phoneNumber);
        body.put("paymentType", "RENT");
        body.put("idempotencyKey", "rent-invoice:" + invoiceId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + bearerToken);

        return restTemplate.postForObject(paymentUrl + "/api/payments/initiate",
                new HttpEntity<>(body, headers), InitiatedPaymentResponse.class);
    }

    @SuppressWarnings("unused")
    private InitiatedPaymentResponse unavailable(String bearerToken, UUID propertyId, UUID landlordId,
                                                 BigDecimal amount, String phoneNumber, UUID invoiceId,
                                                 Throwable t) {
        log.warn("Could not start an M-Pesa prompt for invoice {}: {}", invoiceId, t.getMessage());
        throw new ConflictException("Could not reach M-Pesa just now. Try again in a moment.");
    }
}
