package com.kenyarealestate.pms.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;

@Slf4j
@Component
public class DocumentClient {

    private final RestTemplate restTemplate;
    private final String userUrl;
    private final String internalSecret;

    public DocumentClient(RestTemplate restTemplate,
                          @Value("${services.user-url}") String userUrl,
                          @Value("${services.internal-secret}") String internalSecret) {
        this.restTemplate = restTemplate;
        this.userUrl = userUrl;
        this.internalSecret = internalSecret;
    }

    public record StoredFile(byte[] bytes, MediaType contentType) {}

    public StoredFile fetch(String objectKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        ResponseEntity<Resource> response = restTemplate.exchange(
                userUrl + "/api/documents/internal/files/" + objectKey,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Resource.class);

        Resource body = response.getBody();
        if (body == null) return null;
        try {
            byte[] bytes = body.getInputStream().readAllBytes();
            MediaType type = Objects.requireNonNullElse(
                    response.getHeaders().getContentType(), MediaType.APPLICATION_OCTET_STREAM);
            return new StoredFile(bytes, type);
        } catch (Exception e) {
            log.warn("Could not read stored file {}: {}", objectKey, e.getMessage());
            return null;
        }
    }

    public static String objectKeyFrom(String url) {
        if (url == null) return null;
        int marker = url.indexOf("/files/");
        if (marker < 0) return null;
        String key = url.substring(marker + "/files/".length());
        int query = key.indexOf('?');
        if (query >= 0) key = key.substring(0, query);
        if (key.contains("..") || key.isBlank()) return null;
        return key;
    }
}
