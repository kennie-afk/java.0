package com.mara.core.fiscal;

import com.mara.kit.auth.TerminalRecord;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and takes back blocks of fiscal numbers.
 *
 * <p>The authority numbers invoices; a terminal that is offline cannot ask for one, so it
 * holds a contiguous block in advance. Blocks come from one counter per tenant, advanced under
 * a row lock, so they cannot overlap; a database trigger asserts the same independently.
 * A terminal may hold at most {@code max-open-leases} live blocks, so one cannot hoard the
 * tenant's number space. Numbers handed back unused are voided, never recycled.
 */
@Service
public class FiscalLeaseService {

    public static class Refused extends RuntimeException {
        public final int status;
        public final String code;

        Refused(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    public record Lease(long leaseId, String terminalId, long firstNumber, long lastNumber, long nextNumber,
                        long issuedAtMs, long expiresAtMs) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final long size;
    private final Duration lifetime;
    private final int maxOpen;

    public FiscalLeaseService(
            JdbcTemplate jdbc, Clock maraClock,
            @Value("${mara.fiscal.lease-size:5000}") long size,
            @Value("${mara.fiscal.lease-days:30}") long days,
            @Value("${mara.fiscal.max-open-leases:2}") int maxOpen) {
        if (size < 1 || size > 1_000_000) {
            throw new IllegalStateException("mara.fiscal.lease-size must be between 1 and 1,000,000");
        }
        this.jdbc = jdbc;
        this.clock = maraClock;
        this.size = size;
        this.lifetime = Duration.ofDays(days);
        this.maxOpen = maxOpen;
    }

    /** A lease and whether this answer replays an earlier request instead of taking new numbers. */
    public record Issued(Lease lease, boolean replayed) {
    }

    /**
     * Issues a lease for {@code requestKey}, or returns the one that key already produced.
     * Idempotency is decided after the tenant's counter row is locked, so two identical requests
     * racing each other serialise and the second sees the first's lease.
     */
    @Transactional
    public Issued issue(TerminalRecord terminal, String requestKey) {
        Instant now = clock.instant();
        jdbc.update("INSERT INTO fiscal_series (tenant_id) VALUES (?) ON CONFLICT DO NOTHING", terminal.tenantId());
        long first = jdbc.queryForObject(
                "SELECT next_number FROM fiscal_series WHERE tenant_id = ? FOR UPDATE", Long.class, terminal.tenantId());

        if (requestKey != null) {
            var existing = jdbc.query("""
                    SELECT id, first_number, last_number, issued_at, expires_at FROM fiscal_lease
                     WHERE terminal_id = ? AND request_key = ?""",
                    (rs, i) -> new Lease(rs.getLong("id"), terminal.id(), rs.getLong("first_number"),
                            rs.getLong("last_number"), rs.getLong("first_number"),
                            rs.getTimestamp("issued_at").getTime(), rs.getTimestamp("expires_at").getTime()),
                    terminal.id(), requestKey);
            if (!existing.isEmpty()) {
                return new Issued(existing.get(0), true);
            }
        }

        Integer open = jdbc.queryForObject("""
                SELECT count(*)::int FROM fiscal_lease
                 WHERE terminal_id = ? AND returned_at IS NULL AND expires_at > ?""",
                Integer.class, terminal.id(), Timestamp.from(now));
        if (open != null && open >= maxOpen) {
            throw new Refused(409, "lease_limit", "this terminal already holds " + open
                    + " live fiscal leases; return the unused part of one before asking for another");
        }

        long last = first + size - 1;
        Instant expires = now.plus(lifetime);
        long id = jdbc.queryForObject("""
                INSERT INTO fiscal_lease (tenant_id, terminal_id, first_number, last_number, issued_at, expires_at, request_key)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""",
                Long.class, terminal.tenantId(), terminal.id(), first, last, Timestamp.from(now), Timestamp.from(expires),
                requestKey);
        jdbc.update("UPDATE fiscal_series SET next_number = ? WHERE tenant_id = ?", last + 1, terminal.tenantId());
        return new Issued(new Lease(id, terminal.id(), first, last, first, now.toEpochMilli(), expires.toEpochMilli()), false);
    }

    /**
     * The terminal reports the first number it did NOT use. Everything from there to the end of
     * the lease is voided. Refused if the terminal's own sales already ingested here used a
     * number in that range (it would be voiding something it spent), and idempotent for a
     * repeat of the same return.
     */
    @Transactional
    public Map<String, Object> giveBack(TerminalRecord terminal, long leaseId, long nextUnused) {
        Map<String, Object> lease = jdbc.queryForList("""
                SELECT id, first_number, last_number, returned_at FROM fiscal_lease
                 WHERE id = ? AND terminal_id = ? FOR UPDATE""", leaseId, terminal.id())
                .stream().findFirst().orElseThrow(() -> new Refused(404, "no_such_lease", "no such lease for this terminal"));
        long first = ((Number) lease.get("first_number")).longValue();
        long last = ((Number) lease.get("last_number")).longValue();
        if (nextUnused < first || nextUnused > last + 1) {
            throw new Refused(400, "out_of_range", "nextUnused " + nextUnused + " is outside the lease " + first + ".." + last);
        }
        if (lease.get("returned_at") != null) {
            return Map.of("voidedFrom", nextUnused, "voidedTo", last, "alreadyReturned", true);
        }
        Integer spent = jdbc.queryForObject("""
                SELECT count(*)::int FROM sale
                 WHERE tenant_id = ? AND fiscal_accepted AND fiscal_number BETWEEN ? AND ?""",
                Integer.class, terminal.tenantId(), nextUnused, last);
        if (spent != null && spent > 0) {
            throw new Refused(409, "numbers_in_use", "sales already ingested used numbers at or after " + nextUnused);
        }
        jdbc.update("UPDATE fiscal_lease SET returned_at = ? WHERE id = ?", Timestamp.from(clock.instant()), leaseId);
        if (nextUnused <= last) {
            jdbc.update("""
                    INSERT INTO fiscal_void (tenant_id, lease_id, from_number, to_number, reason)
                    VALUES (?, ?, ?, ?, 'returned unused by terminal')""",
                    terminal.tenantId(), leaseId, nextUnused, last);
        }
        return Map.of("voidedFrom", nextUnused, "voidedTo", last, "alreadyReturned", false);
    }
}
