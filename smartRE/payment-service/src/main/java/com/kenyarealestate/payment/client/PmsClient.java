package com.kenyarealestate.payment.client;

import com.kenyarealestate.payment.dto.RentInvoiceRef;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
public class PmsClient {

    private final RestTemplate restTemplate;

    @Value("${services.pms-url}")
    private String pmsUrl;

    @Value("${services.internal-secret}")
    private String internalSecret;

    public PmsClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.restTemplate = new RestTemplate(factory);
    }

    @CircuitBreaker(name = "pms-service", fallbackMethod = "unavailable")
    public RentInvoiceRef resolveRentInvoice(String reference) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        String encoded = URLEncoder.encode(reference, StandardCharsets.UTF_8);
        return restTemplate.exchange(
                pmsUrl + "/api/pms/internal/invoices/by-reference/" + encoded,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                RentInvoiceRef.class).getBody();
    }

    @SuppressWarnings("unused")
    private RentInvoiceRef unavailable(String reference, Throwable t) {
        log.warn("Could not resolve rent reference '{}' with property-management-service: {}",
                reference, t.getMessage());
        return null;
    }
}
