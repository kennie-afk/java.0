package com.smartseason.agronomy.casework;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Name and email of the person making this request, asked of the identity service with their own
 * token. The email is never taken from the request body: if it were, any signed-in user could
 * make the platform send mail to an address of their choosing.
 */
@Component
public class IdentityDirectory {

    private static final Logger log = LoggerFactory.getLogger(IdentityDirectory.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Person(UUID id, String email, String fullName) {
    }

    private record Cached(Person person, Instant at) {
    }

    private final RestClient client;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public IdentityDirectory(@Value("${smartseason.identity.url:http://identity-service:8081}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.client = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }

    /** The caller's own details, or a person with no email if identity cannot be reached. */
    public Person me(UUID userId) {
        Cached hit = cache.get(userId);
        if (hit != null && hit.at().plus(Duration.ofMinutes(30)).isAfter(Instant.now())) {
            return hit.person();
        }
        String bearer = currentBearer();
        if (bearer == null) {
            return new Person(userId, null, null);
        }
        try {
            Person p = client.get().uri("/api/identity/v1/auth/me").header("Authorization", bearer)
                    .retrieve().body(Person.class);
            if (p != null) {
                cache.put(userId, new Cached(p, Instant.now()));
                return p;
            }
        } catch (RuntimeException ex) {
            log.warn("Could not read profile {} from identity: {}", userId, ex.getMessage());
        }
        return hit != null ? hit.person() : new Person(userId, null, null);
    }

    private static String currentBearer() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getHeader("Authorization");
        }
        return null;
    }
}
