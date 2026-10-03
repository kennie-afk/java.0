package com.mara.core.admin;

import com.mara.core.sales.SyncFeedPoller;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The operator's back-office reads, behind the admin token plus {@code X-Mara-Tenant}. */
@RestController
public class AdminController {

    private final CoreQueryService queries;
    private final SyncFeedPoller poller;

    public AdminController(CoreQueryService queries, SyncFeedPoller poller) {
        this.queries = queries;
        this.poller = poller;
    }

    @GetMapping("/v1/admin/ledger/trial-balance")
    public List<Map<String, Object>> trialBalance() {
        return queries.trialBalance();
    }

    @GetMapping("/v1/admin/sales")
    public List<Map<String, Object>> sales(@RequestParam(defaultValue = "100") int limit) {
        return queries.sales(limit);
    }

    /** Sales by day, terminal or cashier for a date range, in the shop's time zone. */
    @GetMapping("/v1/admin/reports/sales")
    public List<Map<String, Object>> salesReport(
            @RequestParam java.time.LocalDate from, @RequestParam java.time.LocalDate to,
            @RequestParam(defaultValue = "day") String by, @RequestParam(defaultValue = "Africa/Nairobi") String zone) {
        return queries.salesReport(from, to, by, zone);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(CoreQueryService.BadRange.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> badRange(CoreQueryService.BadRange e) {
        return org.springframework.http.ResponseEntity.badRequest().body(Map.of("error", "bad_range", "message", e.getMessage()));
    }

    @GetMapping("/v1/admin/exceptions")
    public List<Map<String, Object>> exceptions(
            @RequestParam(defaultValue = "true") boolean open, @RequestParam(defaultValue = "100") int limit) {
        return queries.exceptions(open, limit);
    }

    @GetMapping("/v1/admin/fiscal/leases")
    public List<Map<String, Object>> leases() {
        return queries.leases();
    }

    /** Pull from sync-service now instead of waiting for the next tick. Idempotent. */
    @PostMapping("/v1/admin/ingest/run")
    public Map<String, Object> run() {
        return Map.of("posted", poller.pollOnce());
    }
}
