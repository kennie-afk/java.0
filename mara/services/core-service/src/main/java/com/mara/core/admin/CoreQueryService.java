package com.mara.core.admin;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Back-office reads for one tenant. Runs inside a transaction so row-level security has its tenant. */
@Service
@Transactional(readOnly = true)
public class CoreQueryService {

    private final JdbcTemplate jdbc;

    public CoreQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Debits and credits per account and currency; the totals must agree, and a test asserts they do. */
    public List<Map<String, Object>> trialBalance() {
        return jdbc.queryForList("""
                SELECT p.account_code AS "account", a.name, p.currency,
                       sum(p.debit_minor)::bigint AS "debitMinor", sum(p.credit_minor)::bigint AS "creditMinor",
                       (sum(p.debit_minor) - sum(p.credit_minor))::bigint AS "balanceMinor"
                  FROM posting p JOIN account a ON a.tenant_id = p.tenant_id AND a.code = p.account_code
                 GROUP BY p.account_code, a.name, p.currency ORDER BY p.account_code, p.currency""");
    }

    public List<Map<String, Object>> sales(int limit) {
        return jdbc.queryForList("""
                SELECT terminal_id AS "terminalId", sequence, occurred_at AS "occurredAt", currency,
                       total_minor AS "totalMinor", fiscal_status AS "fiscalStatus", fiscal_number AS "fiscalNumber",
                       fiscal_accepted AS "fiscalAccepted", consistent, txn_id AS "txnId"
                  FROM sale ORDER BY occurred_at DESC, sequence DESC LIMIT ?""", Math.min(Math.max(limit, 1), 500));
    }

    public List<Map<String, Object>> exceptions(boolean openOnly, int limit) {
        return jdbc.queryForList("""
                SELECT id, terminal_id AS "terminalId", sequence, kind, detail, raised_at AS "raisedAt"
                  FROM core_exception WHERE (? = false OR resolved_at IS NULL)
                 ORDER BY id DESC LIMIT ?""", openOnly, Math.min(Math.max(limit, 1), 500));
    }

    public List<Map<String, Object>> leases() {
        return jdbc.queryForList("""
                SELECT l.id, l.terminal_id AS "terminalId", l.first_number AS "firstNumber", l.last_number AS "lastNumber",
                       l.expires_at AS "expiresAt", l.returned_at AS "returnedAt",
                       (SELECT count(*) FROM sale s WHERE s.tenant_id = l.tenant_id AND s.fiscal_accepted
                           AND s.fiscal_number BETWEEN l.first_number AND l.last_number) AS "used",
                       (SELECT coalesce(sum(v.to_number - v.from_number + 1), 0) FROM fiscal_void v WHERE v.lease_id = l.id) AS "voided"
                  FROM fiscal_lease l ORDER BY l.id DESC LIMIT 200""");
    }
}
