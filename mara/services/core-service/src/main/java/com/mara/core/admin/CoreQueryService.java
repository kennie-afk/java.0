package com.mara.core.admin;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
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

    public static class BadRange extends RuntimeException {
        public BadRange(String message) {
            super(message);
        }
    }

    /**
     * Sales grouped by day, terminal or cashier over an inclusive date range, in the shop's own time zone
     * (a sale at 23:30 in Nairobi belongs to that day, not the next UTC one). Cash and mobile money are what was
     * APPLIED to the sale, so a tendered 1,000 for an 800 sale counts as 800. At most 93 days: the statement scans
     * that range for one tenant, and a bounded range is what keeps it affordable for the largest.
     */
    public List<Map<String, Object>> salesReport(LocalDate from, LocalDate to, String by, String zone) {
        ZoneId z;
        try {
            z = ZoneId.of(zone);
        } catch (RuntimeException e) {
            throw new BadRange("unknown time zone " + zone);
        }
        if (to.isBefore(from)) {
            throw new BadRange("'to' is before 'from'");
        }
        if (to.toEpochDay() - from.toEpochDay() > 92) {
            throw new BadRange("a report covers at most 93 days");
        }
        String group = switch (by) {
            case "day" -> "day";
            case "terminal" -> "day, terminal_id";
            case "cashier" -> "day, cashier";
            default -> throw new BadRange("by must be day, terminal or cashier");
        };
        String columns = switch (by) {
            case "day" -> "day";
            case "terminal" -> "day, terminal_id AS \"terminalId\"";
            default -> "day, cashier AS \"cashierStaffId\"";
        };
        Timestamp start = Timestamp.from(from.atStartOfDay(z).toInstant());
        Timestamp end = Timestamp.from(to.plusDays(1).atStartOfDay(z).toInstant());
        return jdbc.queryForList("""
                WITH per_sale AS (
                    SELECT (s.occurred_at AT TIME ZONE ?)::date AS day, s.terminal_id, coalesce(s.cashier_staff_id, '') AS cashier,
                           s.total_minor, s.tax_minor, s.fiscal_status, s.consistent,
                           coalesce((SELECT sum((x->>'appliedMinor')::bigint) FROM jsonb_array_elements(s.body->'payments') x
                                      WHERE x->>'method' = 'CASH'), 0) AS cash,
                           coalesce((SELECT sum((x->>'appliedMinor')::bigint) FROM jsonb_array_elements(s.body->'payments') x
                                      WHERE x->>'method' = 'MOBILE_MONEY'), 0) AS mobile
                      FROM sale s WHERE s.occurred_at >= ? AND s.occurred_at < ?)
                SELECT %s, count(*)::bigint AS sales, sum(total_minor)::bigint AS "totalMinor", sum(tax_minor)::bigint AS "taxMinor",
                       sum(cash)::bigint AS "cashMinor", sum(mobile)::bigint AS "mobileMinor",
                       count(*) FILTER (WHERE fiscal_status = 'FISCAL_PENDING')::bigint AS "fiscalPending",
                       count(*) FILTER (WHERE NOT consistent)::bigint AS "inconsistent"
                  FROM per_sale GROUP BY %s ORDER BY day DESC, 2 LIMIT 500""".formatted(columns, group),
                zone, start, end);
    }
}
