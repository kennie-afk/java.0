package com.mara.core.sales;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mara.platform.sale.SaleBody;
import com.mara.platform.sale.SaleCheck;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posts one verified sale to the books, atomically: the sale row, its ledger transaction and
 * the advance of the terminal's ingest cursor commit together or not at all. The caller binds
 * the tenant before the transaction begins (row-level security reads it at that instant).
 *
 * <p>The till is authoritative about what happened at its counter, so nothing here refuses a
 * sale. What does not add up is posted anyway, the difference carried to a SUSPENSE account so
 * the books still balance, and an exception raised so a person sees it. A fiscal number that
 * the terminal was never leased, or that another sale already holds, is stored unaccepted
 * and raised; only a number inside this terminal's own lease, not voided, held once, counts.
 *
 * <p>Posting rule for a sale: debit each tender account (CASH, MOBILE_MONEY) the amount
 * applied, credit SALES the net and VAT_PAYABLE the tax. Idempotent: a terminal sequence is
 * posted at most once (a cursor and a unique index both say so), so replaying the feed is safe.
 */
@Service
public class SaleIngestService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public SaleIngestService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public enum Outcome { POSTED, ALREADY_POSTED }

    @Transactional
    public long cursor(String terminalId, String tenantId) {
        jdbc.update("INSERT INTO ingest_cursor (terminal_id, tenant_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                terminalId, tenantId);
        return jdbc.queryForObject("SELECT last_sequence FROM ingest_cursor WHERE terminal_id = ?", Long.class, terminalId);
    }

    @Transactional
    public Outcome ingest(FeedEntry e) {
        String tenant = e.tenantId();
        jdbc.update("INSERT INTO ingest_cursor (terminal_id, tenant_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                e.terminalId(), tenant);
        long last = jdbc.queryForObject(
                "SELECT last_sequence FROM ingest_cursor WHERE terminal_id = ? FOR UPDATE", Long.class, e.terminalId());
        if (e.sequence() <= last) {
            return Outcome.ALREADY_POSTED;
        }
        if (e.sequence() != last + 1) {
            throw new IllegalStateException("feed for " + e.terminalId() + " skipped from " + last + " to " + e.sequence());
        }

        SaleBody sale;
        try {
            sale = json.readValue(e.sale(), SaleBody.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("verified sale " + e.terminalId() + "#" + e.sequence() + " cannot be read", ex);
        }
        SaleCheck.Result check = SaleCheck.check(sale);
        Instant occurred = Instant.ofEpochSecond(e.epochSecond(), e.nano());

        ensureAccounts(tenant);

        boolean numbered = SaleBody.Fiscal.NUMBERED.equals(sale.fiscal().status())
                && sale.fiscal().number().matches("[1-9][0-9]{0,17}");
        Long number = numbered ? Long.parseLong(sale.fiscal().number()) : null;
        boolean accepted = false;
        if (number != null) {
            accepted = classifyFiscal(tenant, e, number);
        }

        Long txnId = null;
        boolean postable = !check.findings().contains(SaleCheck.Finding.MALFORMED_AMOUNT)
                && !check.findings().contains(SaleCheck.Finding.UNKNOWN_CURRENCY)
                && nonNegative(sale)
                && (check.appliedMinor() > 0 || check.netMinor() + check.taxMinor() > 0);
        if (postable) {
            txnId = post(tenant, e, sale, check, occurred);
            if (check.appliedMinor() != check.netMinor() + check.taxMinor()) {
                raise(tenant, e, "SALE_UNBALANCED", "tenders applied " + check.appliedMinor() + " but net+tax is "
                        + (check.netMinor() + check.taxMinor()) + "; the difference of "
                        + Math.abs(check.appliedMinor() - (check.netMinor() + check.taxMinor())) + " is held in SUSPENSE");
            }
        } else {
            raise(tenant, e, "SALE_UNPOSTABLE", "findings " + check.findings() + "; no ledger transaction was posted");
        }

        jdbc.update("""
                INSERT INTO sale (terminal_id, sequence, tenant_id, occurred_at, currency, total_minor, net_minor,
                                  tax_minor, applied_minor, fiscal_status, fiscal_number, fiscal_accepted,
                                  cashier_staff_id, consistent, txn_id, body)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)""",
                e.terminalId(), e.sequence(), tenant, Timestamp.from(occurred), sale.currency(),
                safeLong(sale.totalMinor()), check.netMinor(), check.taxMinor(), check.appliedMinor(),
                sale.fiscal().status(), number, accepted,
                sale.cashier() == null ? null : sale.cashier().staffId(), check.consistent(), txnId, e.sale());

        jdbc.update("UPDATE ingest_cursor SET last_sequence = ? WHERE terminal_id = ?", e.sequence(), e.terminalId());
        return Outcome.POSTED;
    }

    /** A sale with a negative part cannot be posted as debits and credits; it is raised, not forced in. */
    private static boolean nonNegative(SaleBody sale) {
        for (SaleBody.Line l : sale.lines()) {
            if (safeLong(l.netMinor()) < 0 || safeLong(l.taxMinor()) < 0) {
                return false;
            }
        }
        for (SaleBody.Payment p : sale.payments()) {
            if (safeLong(p.appliedMinor()) < 0) {
                return false;
            }
        }
        return true;
    }

    private static long safeLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void ensureAccounts(String tenant) {
        String sql = "INSERT INTO account (tenant_id, code, name, kind) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING";
        jdbc.update(sql, tenant, "CASH", "Cash drawer takings", "ASSET");
        jdbc.update(sql, tenant, "MOBILE_MONEY", "Mobile money receipts", "ASSET");
        jdbc.update(sql, tenant, "SALES", "Sales revenue (net of tax)", "INCOME");
        jdbc.update(sql, tenant, "VAT_PAYABLE", "Tax collected, payable", "LIABILITY");
        jdbc.update(sql, tenant, "SUSPENSE", "Unexplained differences from terminals", "SUSPENSE");
    }

    /** Whether this sale's fiscal number counts: leased to this terminal, not voided, not already held. */
    private boolean classifyFiscal(String tenant, FeedEntry e, long number) {
        Integer leased = jdbc.queryForObject("""
                SELECT count(*)::int FROM fiscal_lease l
                 WHERE l.terminal_id = ? AND l.first_number <= ? AND ? <= l.last_number
                   AND NOT EXISTS (SELECT 1 FROM fiscal_void v WHERE v.lease_id = l.id
                                    AND v.from_number <= ? AND ? <= v.to_number)""",
                Integer.class, e.terminalId(), number, number, number, number);
        if (leased == null || leased == 0) {
            raise(tenant, e, "FISCAL_OUT_OF_LEASE",
                    "fiscal number " + number + " was never leased to this terminal, or was returned and voided");
            return false;
        }
        Integer holders = jdbc.queryForObject(
                "SELECT count(*)::int FROM sale WHERE tenant_id = ? AND fiscal_number = ? AND fiscal_accepted",
                Integer.class, tenant, number);
        if (holders != null && holders > 0) {
            raise(tenant, e, "FISCAL_DUPLICATE", "fiscal number " + number + " already stands for another sale");
            return false;
        }
        return true;
    }

    private long post(String tenant, FeedEntry e, SaleBody sale, SaleCheck.Result check, Instant occurred) {
        long txn = jdbc.queryForObject("""
                INSERT INTO ledger_txn (tenant_id, kind, currency, occurred_at, source_terminal, source_sequence, memo)
                VALUES (?, 'SALE', ?, ?, ?, ?, ?) RETURNING id""",
                Long.class, tenant, sale.currency(), Timestamp.from(occurred), e.terminalId(), e.sequence(),
                "sale " + e.terminalId() + "#" + e.sequence());

        long cash = 0;
        long mobile = 0;
        for (SaleBody.Payment p : sale.payments()) {
            long amount = Long.parseLong(p.appliedMinor());
            if ("CASH".equals(p.method())) {
                cash += amount;
            } else {
                mobile += amount;
            }
        }
        List<Object[]> lines = new ArrayList<>();
        addIfPositive(lines, "CASH", cash, true);
        addIfPositive(lines, "MOBILE_MONEY", mobile, true);
        addIfPositive(lines, "SALES", check.netMinor(), false);
        addIfPositive(lines, "VAT_PAYABLE", check.taxMinor(), false);
        long debits = cash + mobile;
        long credits = check.netMinor() + check.taxMinor();
        if (debits > credits) {
            addIfPositive(lines, "SUSPENSE", debits - credits, false);
        } else if (credits > debits) {
            addIfPositive(lines, "SUSPENSE", credits - debits, true);
        }
        for (Object[] l : lines) {
            jdbc.update("""
                    INSERT INTO posting (txn_id, tenant_id, account_code, currency, debit_minor, credit_minor)
                    VALUES (?, ?, ?, ?, ?, ?)""",
                    txn, tenant, l[0], sale.currency(), (boolean) l[2] ? l[1] : 0L, (boolean) l[2] ? 0L : l[1]);
        }
        return txn;
    }

    private static void addIfPositive(List<Object[]> lines, String account, long amount, boolean debit) {
        if (amount > 0) {
            lines.add(new Object[] {account, amount, debit});
        }
    }

    private void raise(String tenant, FeedEntry e, String kind, String detail) {
        jdbc.update("""
                INSERT INTO core_exception (tenant_id, terminal_id, sequence, kind, detail)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING""", tenant, e.terminalId(), e.sequence(), kind, detail);
    }
}
