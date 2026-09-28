package com.kenyarealestate.pms.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Sends mail that is not derived from a domain event.
 *
 * <p>Modelled on user-service's client of the same name, which password reset already uses.
 * The one interesting parameter is the pair {@code userId} / {@code recipientEmail}: an
 * invited tenant has no account yet, and {@code userId} is required by the endpoint, so the
 * invitation is sent as the **landlord's** notification with the tenant's address as the
 * recipient override. The dispatcher skips its contact lookup entirely when
 * {@code recipientEmail} is present, so delivery goes to the tenant while the record sits in
 * the history of the person who actually performed the action — which is also the right
 * party for an opt-out to be read from.
 */
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
    public boolean send(UUID actorUserId, String templateCode, String recipientEmail,
                        String dedupKey, String actionUrl, Map<String, Object> model) {

        Map<String, Object> body = new HashMap<>();
        body.put("userId", actorUserId.toString());
        body.put("templateCode", templateCode);
        body.put("recipientEmail", recipientEmail);
        body.put("dedupKey", dedupKey);
        body.put("actionUrl", actionUrl);
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
    private boolean sendFallback(UUID actorUserId, String templateCode, String recipientEmail,
                                 String dedupKey, String actionUrl, Map<String, Object> model,
                                 Throwable t) {
        // Returning false rather than throwing. The caller has already committed the
        // invitation, so a mail outage must not undo it — the landlord is told the mail did
        // not go out and can send it again, and the token they were shown still works.
        log.warn("notification-service unavailable for templateCode={} ({})",
                templateCode, t.getMessage());
        return false;
    }
}
