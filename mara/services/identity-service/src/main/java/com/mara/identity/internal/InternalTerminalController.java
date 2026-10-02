package com.mara.identity.internal;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * What sync-service and core-service ask the trust root: which tenant owns this terminal,
 * what is its public key, is it still active. Answers only for one terminal id the caller
 * already holds, through the same {@code resolve_terminal} function sign-in uses, so it
 * cannot be used to enumerate terminals or read anything else.
 */
@RestController
public class InternalTerminalController {

    private final JdbcTemplate jdbc;

    public InternalTerminalController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/v1/internal/terminals/{id}")
    public ResponseEntity<Map<String, Object>> terminal(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT tenant_id, branch_id, public_key, status FROM resolve_terminal(?)", id);
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> r = rows.get(0);
        return ResponseEntity.ok(Map.of(
                "tenantId", r.get("tenant_id"),
                "branchId", r.get("branch_id"),
                "publicKey", r.get("public_key"),
                "status", r.get("status")));
    }
}
