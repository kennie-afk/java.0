package com.mara.kit.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mara.platform.credential.OperatorToken;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks identity-service whether a presented credential may make a request, authenticating itself
 * with this service's own service credential ({@code credentials:verify}).
 *
 * <p>Only an {@code ok} answer is cached, for a short window (default 15 s), keyed by the exact
 * credential, scope and tenant: a revoked credential stops working within that window, and a
 * credential issued a second ago works at once because refusals are never cached. If identity-service
 * cannot be reached the answer is 503, a refusal: this fails closed, never open.
 */
public class HttpCredentialVerifier implements CredentialVerifier {

    private record Cached(Decision decision, long atMs) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final String baseUrl;
    private final String serviceCredential;
    private final long ttlMs;
    private final Clock clock;

    public HttpCredentialVerifier(String baseUrl, String serviceCredential, Duration ttl, Clock clock) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.serviceCredential = serviceCredential;
        this.ttlMs = ttl.toMillis();
        this.clock = clock;
    }

    @Override
    public Decision verify(Request r) {
        String key = HexFormat.of().formatHex(OperatorToken.hash(r.presented() + "|" + r.requiredScope() + "|" + r.tenantHeader()));
        long now = clock.millis();
        Cached hit = cache.get(key);
        if (hit != null && now - hit.atMs() < ttlMs) {
            return hit.decision();
        }
        try {
            String body = json.writeValueAsString(Map.of(
                    "credential", r.presented(), "requiredScope", r.requiredScope(),
                    "tenant", r.tenantHeader() == null ? "" : r.tenantHeader(),
                    "method", r.method(), "path", r.path(), "remoteAddr", r.remoteAddr() == null ? "" : r.remoteAddr()));
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/v1/internal/credentials/verify"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + serviceCredential)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // identity refused OUR credential, or failed: nothing can be said about theirs
                return Decision.denied(503, "credential_service_unavailable");
            }
            JsonNode n = json.readTree(response.body());
            Decision d = n.path("ok").asBoolean()
                    ? Decision.allowed(n.path("credentialId").asText(), n.path("label").asText(),
                            n.path("tenantId").isNull() || n.path("tenantId").isMissingNode() ? null : n.path("tenantId").asText())
                    : Decision.denied(n.path("status").asInt(401), n.path("reason").asText("unauthorised"));
            if (d.ok()) {
                cache.put(key, new Cached(d, now));
            } else {
                cache.remove(key);
            }
            return d;
        } catch (IOException e) {
            return Decision.denied(503, "credential_service_unavailable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Decision.denied(503, "credential_service_unavailable");
        }
    }
}
