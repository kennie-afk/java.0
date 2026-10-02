package com.mara.sync.admin;

import com.mara.sync.journal.JournalQueryService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service feed ({@code /v1/internal}, internal token) and the operator's
 * back-office reads ({@code /v1/admin}, admin token plus {@code X-Mara-Tenant}).
 */
@RestController
public class InternalAndAdminController {

    private final JournalQueryService queries;

    public InternalAndAdminController(JournalQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/v1/internal/chains")
    public List<Map<String, Object>> chains(
            @RequestParam(defaultValue = "") String after, @RequestParam(defaultValue = "200") int limit) {
        return queries.chainHeads(after, limit);
    }

    @GetMapping("/v1/internal/terminals/{terminalId}/entries")
    public ResponseEntity<List<Map<String, Object>>> entries(
            @PathVariable String terminalId,
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "200") int limit,
            @RequestHeader(value = "X-Mara-Tenant", required = false) String tenant) {
        if (tenant == null || tenant.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(queries.entries(terminalId, after, limit));
    }

    @GetMapping("/v1/admin/exceptions")
    public List<Map<String, Object>> exceptions(
            @RequestParam(defaultValue = "true") boolean open, @RequestParam(defaultValue = "100") int limit) {
        return queries.exceptions(open, limit);
    }

    @GetMapping("/v1/admin/chains")
    public List<Map<String, Object>> adminChains() {
        return queries.chains();
    }
}
