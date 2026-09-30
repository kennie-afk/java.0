package com.mara.identity.admin;

import com.mara.platform.identity.EnrolmentCode;
import com.mara.platform.staff.StaffRole;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates tenants, branches, staff and enrolment codes.
 *
 * <p>Every write goes through the same row-level policies as any other: creating a tenant
 * mints its id server-side and adopts it for the transaction, exactly as enrolment adopts
 * the tenant it derives from a code, so the insert is checked against a tenant the server
 * chose rather than one a caller asserted.
 */
@Service
public class ProvisioningService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final PinHasher pins;
    private final Clock clock;

    public ProvisioningService(JdbcTemplate jdbc, PinHasher pins, Clock clock) {
        this.jdbc = jdbc;
        this.pins = pins;
        this.clock = clock;
    }

    public static class Refused extends RuntimeException {
        public Refused(String message) {
            super(message);
        }
    }

    public record TenantCreated(String tenantId, String branchId, String ownerStaffId, String ownerStaffNumber) {
    }

    @Transactional
    public TenantCreated createTenant(
            String legalName, String tradingName, String countryCode, String currency,
            String taxIdentifier, int licensedTerminals,
            String branchName, String timezone,
            String ownerName, String ownerStaffNumber, String ownerPin) {

        String refusal = PinHasher.refusalFor(ownerPin);
        if (refusal != null) {
            throw new Refused(refusal);
        }
        String tenantId = id("TEN");
        String branchId = id("BR");
        String ownerId = id("STF");
        bind(tenantId);

        jdbc.update(
                "INSERT INTO tenant (id, legal_name, trading_name, country_code, default_currency, tax_identifier, licensed_terminals) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                tenantId, legalName, tradingName, countryCode.toUpperCase(), currency.toUpperCase(),
                taxIdentifier, licensedTerminals);
        jdbc.update(
                "INSERT INTO branch (id, tenant_id, name, timezone) VALUES (?, ?, ?, ?)",
                branchId, tenantId, branchName, timezone);
        jdbc.update(
                "INSERT INTO staff (id, tenant_id, branch_id, display_name, role, pin_hash, staff_number) "
                        + "VALUES (?, ?, NULL, ?, 'OWNER', ?, ?)",
                ownerId, tenantId, ownerName, pins.hash(ownerPin), ownerStaffNumber);
        audit(tenantId, "admin", null, "tenant.create", "OK", tradingName);
        return new TenantCreated(tenantId, branchId, ownerId, ownerStaffNumber);
    }

    @Transactional
    public String createBranch(String tenantId, String name, String timezone) {
        bind(tenantId);
        requireTenant(tenantId);
        String branchId = id("BR");
        jdbc.update("INSERT INTO branch (id, tenant_id, name, timezone) VALUES (?, ?, ?, ?)",
                branchId, tenantId, name, timezone);
        audit(tenantId, "admin", null, "branch.create", "OK", name);
        return branchId;
    }

    @Transactional
    public String createStaff(
            String tenantId, String branchId, String displayName, String role, String staffNumber, String pin) {
        bind(tenantId);
        requireTenant(tenantId);
        StaffRole parsed;
        try {
            parsed = StaffRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new Refused("Unknown role " + role + ".");
        }
        String refusal = PinHasher.refusalFor(pin);
        if (refusal != null) {
            throw new Refused(refusal);
        }
        if (!parsed.spansBranches()) {
            if (branchId == null) {
                throw new Refused("A " + role + " belongs to exactly one branch.");
            }
            Integer found = jdbc.queryForObject("SELECT count(*)::int FROM branch WHERE id = ?", Integer.class, branchId);
            if (found == null || found == 0) {
                throw new Refused("No such branch in this tenant.");
            }
        }
        String staffId = id("STF");
        jdbc.update(
                "INSERT INTO staff (id, tenant_id, branch_id, display_name, role, pin_hash, staff_number) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                staffId, tenantId, parsed.spansBranches() ? null : branchId, displayName, role, pins.hash(pin), staffNumber);
        audit(tenantId, "admin", null, "staff.create", "OK", staffNumber + " " + role);
        return staffId;
    }

    public record IssuedCode(String code, String branchId, Instant expiresAt) {
    }

    /** The plaintext code exists only in this return value; the database stores its hash. */
    @Transactional
    public IssuedCode issueEnrolmentCode(String tenantId, String branchId, String issuedByStaffId) {
        bind(tenantId);
        requireTenant(tenantId);
        Integer branch = jdbc.queryForObject("SELECT count(*)::int FROM branch WHERE id = ?", Integer.class, branchId);
        Integer issuer = jdbc.queryForObject("SELECT count(*)::int FROM staff WHERE id = ? AND status = 'ACTIVE'",
                Integer.class, issuedByStaffId);
        if (branch == null || branch == 0) {
            throw new Refused("No such branch in this tenant.");
        }
        if (issuer == null || issuer == 0) {
            throw new Refused("The issuing staff member does not exist or is not active.");
        }
        String code = EnrolmentCode.generate();
        Instant now = clock.instant();
        Instant expires = now.plus(EnrolmentCode.DEFAULT_VALIDITY);
        jdbc.update(
                "INSERT INTO enrolment_code (id, tenant_id, branch_id, code_hash, issued_by, issued_at, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id("ENR"), tenantId, branchId, EnrolmentCode.hash(code), issuedByStaffId,
                Timestamp.from(now), Timestamp.from(expires));
        audit(tenantId, issuedByStaffId, null, "enrolment.issue", "OK", branchId);
        return new IssuedCode(code, branchId, expires);
    }

    @Transactional
    public void setStaffStatus(String tenantId, String staffId, String status) {
        bind(tenantId);
        int n = jdbc.update("UPDATE staff SET status = ? WHERE id = ?", status, staffId);
        if (n == 0) {
            throw new Refused("No such staff member.");
        }
        audit(tenantId, "admin", null, "staff.status", "OK", staffId + " -> " + status);
    }

    @Transactional
    public void setTerminalStatus(String tenantId, String terminalId, String status) {
        bind(tenantId);
        int n = jdbc.update("UPDATE terminal SET status = ? WHERE id = ?", status, terminalId);
        if (n == 0) {
            throw new Refused("No such terminal.");
        }
        audit(tenantId, "admin", terminalId, "terminal.status", "OK", status);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStaff(String tenantId) {
        bind(tenantId);
        return jdbc.queryForList(
                "SELECT id, staff_number, display_name, role, branch_id, status, failed_attempts, locked_until "
                        + "FROM staff ORDER BY staff_number");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTerminals(String tenantId) {
        bind(tenantId);
        return jdbc.queryForList(
                "SELECT id, label, branch_id, status, enrolled_at, last_seen_at FROM terminal ORDER BY created_at");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listBranches(String tenantId) {
        bind(tenantId);
        return jdbc.queryForList("SELECT id, name, timezone, status FROM branch ORDER BY created_at");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> auditTrail(String tenantId, int limit) {
        bind(tenantId);
        return jdbc.queryForList(
                "SELECT id, occurred_at, actor, terminal_id, action, outcome, detail FROM audit_log "
                        + "ORDER BY id DESC LIMIT ?", Math.min(Math.max(limit, 1), 500));
    }

    public void audit(String tenantId, String actor, String terminalId, String action, String outcome, String detail) {
        jdbc.update(
                "INSERT INTO audit_log (tenant_id, actor, terminal_id, action, outcome, detail) VALUES (?, ?, ?, ?, ?, ?)",
                tenantId, actor, terminalId, action, outcome, detail);
    }

    private void bind(String tenantId) {
        jdbc.query("SELECT set_config('mara.tenant_id', ?, true)", rs -> null, tenantId);
    }

    private void requireTenant(String tenantId) {
        Integer n = jdbc.queryForObject("SELECT count(*)::int FROM tenant WHERE id = ?", Integer.class, tenantId);
        if (n == null || n == 0) {
            throw new Refused("No such tenant.");
        }
    }

    private static String id(String prefix) {
        byte[] entropy = new byte[8];
        RANDOM.nextBytes(entropy);
        return prefix + "-" + HexFormat.of().formatHex(entropy).toUpperCase();
    }
}
