package com.kenyarealestate.user.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class NotificationClient {

    private final RestTemplate restTemplate;

    @Value("${services.notification-url}")
    private String notificationUrl;

    @Value("${services.internal-secret}")
    private String internalSecret;

    public NotificationClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restTemplate = new RestTemplate(factory);
    }

    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendFallback")
    @Retry(name = "notification-service")
    public boolean send(UUID userId, String templateCode, String recipientEmail,
                        String dedupKey, Map<String, Object> model) {

        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId.toString());
        body.put("templateCode", templateCode);
        body.put("recipientEmail", recipientEmail);
        body.put("dedupKey", dedupKey);
        body.put("model", model);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Secret", internalSecret);

        restTemplate.postForEntity(
                notificationUrl + "/api/notifications/internal/send",
                new HttpEntity<>(body, headers),
                Void.class);
        return true;
    }

    @SuppressWarnings("unused")
    private boolean sendFallback(UUID userId, String templateCode, String recipientEmail,
                                 String dedupKey, Map<String, Object> model, Throwable t) {
        log.warn("notification-service unavailable for templateCode={} userId={} ({}); "
                + "caller will fall back to direct delivery", templateCode, userId, t.getMessage());
        return false;
    }
}
