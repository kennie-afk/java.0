package com.mara.core.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mara.kit.tenant.TenantContext;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pulls verified journal entries from sync-service and posts them, per terminal and in
 * sequence order. A pull, not a push, so core-service is never in the path of a till's
 * upload: if it is down or slow, terminals still sync, and it catches up from its own cursors
 * when it returns. A terminal that fails to post (a sale the books refuse) is logged and left
 * at its cursor; it never stops another terminal's sales from being posted.
 */
@Component
public class SyncFeedPoller {

    private static final Logger log = LoggerFactory.getLogger(SyncFeedPoller.class);
    private static final int PAGE = 200;

    private final SaleIngestService ingest;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String baseUrl;
    private final String token;

    public SyncFeedPoller(
            SaleIngestService ingest,
            @Value("${mara.sync.base-url}") String baseUrl,
            @Value("${mara.service.credential}") String token) {
        this.ingest = ingest;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
    }

    @Scheduled(fixedDelayString = "${mara.sync.poll-interval-ms:5000}", initialDelayString = "${mara.sync.initial-delay-ms:5000}")
    public void scheduled() {
        try {
            pollOnce();
        } catch (RuntimeException e) {
            log.warn("feed poll failed: {}", e.toString());
        }
    }

    /** One full pass over every terminal; returns how many sales were posted. */
    public int pollOnce() {
        int posted = 0;
        String after = "";
        while (true) {
            List<JsonNode> heads = getArray("/v1/internal/chains?limit=200&after=" + enc(after), null);
            if (heads.isEmpty()) {
                return posted;
            }
            for (JsonNode head : heads) {
                String terminal = head.get("terminalId").asText();
                String tenant = head.get("tenantId").asText();
                long headSeq = head.get("lastSequence").asLong();
                try {
                    posted += catchUp(terminal, tenant, headSeq);
                } catch (RuntimeException e) {
                    log.error("terminal {} cannot be posted past its cursor: {}", terminal, e.toString());
                }
                after = terminal;
            }
        }
    }

    private int catchUp(String terminal, String tenant, long headSeq) {
        int posted = 0;
        long cursor = TenantContext.with(tenant, () -> ingest.cursor(terminal, tenant));
        while (cursor < headSeq) {
            List<JsonNode> page = getArray(
                    "/v1/internal/terminals/" + terminal + "/entries?limit=" + PAGE + "&after=" + cursor, tenant);
            if (page.isEmpty()) {
                return posted;
            }
            for (JsonNode n : page) {
                FeedEntry entry = new FeedEntry(
                        n.get("terminalId").asText(), n.get("tenantId").asText(), n.get("sequence").asLong(),
                        n.get("epochSecond").asLong(), n.get("nano").asInt(), n.get("sale").asText());
                SaleIngestService.Outcome o = TenantContext.with(tenant, () -> ingest.ingest(entry));
                if (o == SaleIngestService.Outcome.POSTED) {
                    posted++;
                }
                cursor = entry.sequence();
            }
        }
        return posted;
    }

    private List<JsonNode> getArray(String path, String tenant) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + token)
                    .GET();
            if (tenant != null) {
                b.header("X-Mara-Tenant", tenant);
            }
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw new IllegalStateException("sync-service answered " + r.statusCode() + " for " + path);
            }
            List<JsonNode> out = new ArrayList<>();
            json.readTree(r.body()).forEach(out::add);
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("sync-service unreachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
