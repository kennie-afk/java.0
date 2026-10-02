package com.mara.sync.journal;

import java.util.List;
import java.util.Map;
import com.mara.kit.auth.TerminalRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads. Each runs inside a transaction because tenant scoping only exists inside one
 * (the tenant is bound when the transaction begins); a read outside it would see nothing.
 */
@Service
@Transactional(readOnly = true)
public class JournalQueryService {

    private final JdbcTemplate jdbc;

    public JournalQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> status(TerminalRecord terminal) {
        List<Map<String, Object>> head = jdbc.queryForList(
                "SELECT last_sequence, encode(head_digest, 'hex') AS head_digest FROM chain_head WHERE terminal_id = ?",
                terminal.id());
        Integer open = jdbc.queryForObject(
                "SELECT count(*)::int FROM sync_exception WHERE terminal_id = ? AND resolved_at IS NULL",
                Integer.class, terminal.id());
        Integer gaps = jdbc.queryForObject(
                "SELECT count(*)::int FROM sync_exception WHERE terminal_id = ? AND kind = 'GAP' AND resolved_at IS NULL",
                Integer.class, terminal.id());
        long last = head.isEmpty() ? 0 : ((Number) head.get(0).get("last_sequence")).longValue();
        return Map.of(
                "lastSequence", last,
                "headDigest", head.isEmpty() ? "" : head.get(0).get("head_digest"),
                "openExceptions", open == null ? 0 : open,
                "heldAtGap", gaps != null && gaps > 0);
    }

    /** Terminals with a verified chain, across tenants, for core-service's poller. Identifiers only. */
    public List<Map<String, Object>> chainHeads(String after, int limit) {
        return jdbc.queryForList(
                "SELECT terminal_id AS \"terminalId\", tenant_id AS \"tenantId\", last_sequence AS \"lastSequence\" "
                        + "FROM list_chain_heads(?, ?)", after == null ? "" : after, limit);
    }

    /** One terminal's verified entries after a sequence, ascending: the feed core-service posts to the ledger. */
    public List<Map<String, Object>> entries(String terminalId, long after, int limit) {
        return jdbc.queryForList("""
                SELECT sequence, tenant_id AS "tenantId", terminal_id AS "terminalId",
                       epoch_second AS "epochSecond", nano, sale::text AS sale,
                       encode(digest, 'hex') AS digest
                  FROM journal_entry
                 WHERE terminal_id = ? AND sequence > ?
                 ORDER BY sequence
                 LIMIT ?""", terminalId, after, Math.min(Math.max(limit, 1), 500));
    }

    public List<Map<String, Object>> exceptions(boolean openOnly, int limit) {
        return jdbc.queryForList("""
                SELECT id, terminal_id AS "terminalId", sequence, kind, detail, raised_at AS "raisedAt",
                       resolved_at AS "resolvedAt"
                  FROM sync_exception
                 WHERE (? = false OR resolved_at IS NULL)
                 ORDER BY id DESC LIMIT ?""", openOnly, Math.min(Math.max(limit, 1), 500));
    }

    public List<Map<String, Object>> chains() {
        return jdbc.queryForList("""
                SELECT terminal_id AS "terminalId", last_sequence AS "lastSequence",
                       encode(head_digest, 'hex') AS "headDigest", updated_at AS "updatedAt"
                  FROM chain_head ORDER BY terminal_id LIMIT 500""");
    }
}
