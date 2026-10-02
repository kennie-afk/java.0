package com.mara.kit.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads terminals from identity-service's internal endpoint, with a short positive cache.
 *
 * <p>The window is deliberately short (default 30 s): a suspended terminal must stop being
 * believed quickly, while every sale upload and lease request would otherwise be one more
 * request to the trust root. Misses and errors are never cached, so a terminal enrolled a
 * second ago is not refused for a minute, and an unreachable identity-service fails closed
 * (the lookup is empty, the request is refused) instead of admitting on stale data beyond
 * the window.
 */
public class HttpTerminalDirectory implements TerminalDirectory {

    private record Cached(TerminalRecord record, long atMs) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final String baseUrl;
    private final String token;
    private final long ttlMs;
    private final Clock clock;

    public HttpTerminalDirectory(String baseUrl, String token, Duration ttl, Clock clock) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
        this.ttlMs = ttl.toMillis();
        this.clock = clock;
    }

    @Override
    public Optional<TerminalRecord> find(String terminalId) {
        if (terminalId == null || !terminalId.matches("TERM-[0-9A-F]{20}")) {
            return Optional.empty();
        }
        long now = clock.millis();
        Cached hit = cache.get(terminalId);
        if (hit != null && now - hit.atMs() < ttlMs) {
            return Optional.of(hit.record());
        }
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/v1/internal/terminals/" + terminalId))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + token)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                cache.remove(terminalId);
                return Optional.empty();
            }
            JsonNode n = json.readTree(response.body());
            TerminalRecord record = new TerminalRecord(
                    terminalId, n.path("tenantId").asText(), n.path("branchId").asText(),
                    n.path("publicKey").asText(), n.path("status").asText());
            cache.put(terminalId, new Cached(record, now));
            return Optional.of(record);
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
