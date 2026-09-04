package com.kenyarealestate.pms.client;

import com.kenyarealestate.pms.dto.PropertySummary;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Component
public class PropertyServiceClient {

    private final RestTemplate restTemplate;
    private final String propertyUrl;
    private final String internalSecret;

    public PropertyServiceClient(RestTemplate restTemplate,
                                 @Value("${services.property-url}") String propertyUrl,
                                 @Value("${services.internal-secret}") String internalSecret) {
        this.restTemplate = restTemplate;
        this.propertyUrl = propertyUrl;
        this.internalSecret = internalSecret;
    }

    @CircuitBreaker(name = "property-service", fallbackMethod = "unavailable")
    @Retry(name = "property-service")
    public PropertySummary getProperty(UUID propertyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        return restTemplate.exchange(
                propertyUrl + "/api/properties/internal/" + propertyId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                PropertySummary.class).getBody();
    }

    @SuppressWarnings("unused")
    private PropertySummary unavailable(UUID propertyId, Throwable t) {
        log.warn("Could not load property {} from property-service: {}", propertyId, t.getMessage());
        return null;
    }
}
