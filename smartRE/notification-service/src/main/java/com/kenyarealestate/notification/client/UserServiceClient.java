package com.kenyarealestate.notification.client;

import com.kenyarealestate.notification.dto.UserContactResponse;
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
public class UserServiceClient {

    private final RestTemplate restTemplate;
    private final String userUrl;
    private final String internalSecret;

    public UserServiceClient(RestTemplate restTemplate,
                             @Value("${services.user-url}") String userUrl,
                             @Value("${services.internal-secret}") String internalSecret) {
        this.restTemplate = restTemplate;
        this.userUrl = userUrl;
        this.internalSecret = internalSecret;
    }

    @CircuitBreaker(name = "user-service", fallbackMethod = "contactUnavailable")
    @Retry(name = "user-service")
    public UserContactResponse getContact(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        var response = restTemplate.exchange(
                userUrl + "/api/users/internal/" + userId + "/contact",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                UserContactResponse.class);
        return response.getBody();
    }

    @SuppressWarnings("unused")
    private UserContactResponse contactUnavailable(UUID userId, Throwable t) {
        log.warn("Could not resolve contact for userId={} ({}); will retry on the next sweep",
                userId, t.getMessage());
        return null;
    }
}
