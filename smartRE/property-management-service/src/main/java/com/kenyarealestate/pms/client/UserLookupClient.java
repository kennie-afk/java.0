package com.kenyarealestate.pms.client;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Resolves a SmartRE account id from an email address.
 *
 * <p>Exists so that linking a tenant record to an account cannot be done with an
 * arbitrary id. The landlord supplies no id at all — the server looks up the address
 * already stored on the tenant record, which the landlord recorded when they created
 * it. That constrains linking to the person the landlord already claimed to be
 * housing, rather than to anyone whose id they can guess.
 */
@Component
public class UserLookupClient {

    private static final Logger log = LoggerFactory.getLogger(UserLookupClient.class);

    private final RestTemplate rest = new RestTemplate();
    private final String userUrl;
    private final String internalSecret;

    public UserLookupClient(@Value("${services.user-url}") String userUrl,
                            @Value("${services.internal-secret}") String internalSecret) {
        this.userUrl = userUrl;
        this.internalSecret = internalSecret;
    }

    /** @return the account id for that address, or empty if nobody has registered with it */
    @SuppressWarnings("rawtypes")
    public Optional<UUID> findIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        try {
            String uri = UriComponentsBuilder.fromHttpUrl(userUrl)
                    .path("/api/users/internal/resolve")
                    .queryParam("email", email.trim())
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Secret", internalSecret);

            ResponseEntity<Map> response =
                    rest.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Object id = response.getBody() == null ? null : response.getBody().get("userId");
            return id == null ? Optional.empty() : Optional.of(UUID.fromString(id.toString()));

        } catch (Exception e) {
            // A miss and an outage look the same to the caller on purpose: neither
            // should tell a landlord whether a given address is registered.
            log.debug("Could not resolve an account for that address: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
