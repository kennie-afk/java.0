package com.mara.sync.journal;

import com.mara.kit.auth.TerminalAuthFilter;
import com.mara.kit.auth.TerminalRecord;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The terminal-facing surface. Authentication (an Ed25519-signed request from an ACTIVE
 * terminal) is done by {@code TerminalAuthFilter} before either method runs, and the tenant
 * comes from identity-service's record of that terminal, never from the request.
 */
@RestController
public class JournalController {

    private final JournalIngestService ingest;
    private final JournalQueryService queries;

    public JournalController(JournalIngestService ingest, JournalQueryService queries) {
        this.ingest = ingest;
        this.queries = queries;
    }

    public record Upload(List<UploadedEntry> entries) {
    }

    @PostMapping("/v1/terminal/sync/journal")
    public ResponseEntity<?> upload(@RequestBody Upload body, HttpServletRequest request) {
        TerminalRecord terminal = (TerminalRecord) request.getAttribute(TerminalAuthFilter.TERMINAL_ATTRIBUTE);
        if (body == null || body.entries() == null || body.entries().isEmpty()
                || body.entries().size() > JournalIngestService.MAX_BATCH) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "an upload carries between 1 and " + JournalIngestService.MAX_BATCH + " entries"));
        }
        return ResponseEntity.ok(ingest.ingest(terminal, body.entries()));
    }

    @GetMapping("/v1/terminal/sync/status")
    public Map<String, Object> status(HttpServletRequest request) {
        return queries.status((TerminalRecord) request.getAttribute(TerminalAuthFilter.TERMINAL_ATTRIBUTE));
    }
}
