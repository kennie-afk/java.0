package com.mara.identity.staff;

import com.mara.identity.admin.PinHasher;
import com.mara.platform.identity.TerminalSignature;
import com.mara.platform.staff.PinPolicy;
import com.mara.platform.staff.SignInOutcome;
import com.mara.platform.staff.StaffRole;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signs staff in at a terminal.
 *
 * <p>Three independent checks, in this order, each refusing identically from the caller's
 * point of view: the terminal proves who it is (an Ed25519 signature with the key it
 * enrolled with), the request is fresh, and only then is the PIN examined, under the
 * pure {@link PinPolicy}'s lockout and branch rules. Failures are recorded and COMMIT —
 * the lockout counter must survive the refusal it is counting.
 */
@Service
public class StaffSignInService {

    static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(2);

    private final JdbcTemplate jdbc;
    private final PinHasher pins;
    private final PinPolicy policy = new PinPolicy();
    private final Clock clock;

    public StaffSignInService(JdbcTemplate jdbc, PinHasher pins, Clock clock) {
        this.jdbc = jdbc;
        this.pins = pins;
        this.clock = clock;
    }

    public record Result(
            SignInOutcome outcome, String staffId, String staffNumber, String displayName, String role, String branchId) {
    }

    /** What the terminal signs: binds the attempt to this terminal, this staff number and this moment. */
    public static byte[] signedMessage(String terminalId, String staffNumber, long epochSecond) {
        return ("mara.staff-signin.v1|" + terminalId + "|" + staffNumber + "|" + epochSecond)
                .getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public Result signIn(String terminalId, String staffNumber, String pin, long epochSecond, String signatureHex) {
        Instant now = clock.instant();

        List<Map<String, Object>> found = jdbc.queryForList(
                "SELECT tenant_id, branch_id, public_key, status FROM resolve_terminal(?)", terminalId);
        if (found.isEmpty()) {
            pins.matches(pin, null); // same cost as a real attempt
            return refused();
        }
        Map<String, Object> terminal = found.get(0);
        String tenantId = (String) terminal.get("tenant_id");
        String terminalBranch = (String) terminal.get("branch_id");

        boolean signed = verifySignature((String) terminal.get("public_key"), terminalId, staffNumber, epochSecond, signatureHex);
        boolean fresh = Math.abs(Duration.between(Instant.ofEpochSecond(epochSecond), now).toSeconds())
                <= MAX_CLOCK_SKEW.toSeconds();
        boolean active = "ACTIVE".equals(terminal.get("status"));

        jdbc.query("SELECT set_config('mara.tenant_id', ?, true)", rs -> null, tenantId);

        if (!signed || !fresh || !active) {
            pins.matches(pin, null);
            audit(tenantId, "terminal:" + terminalId, terminalId, "REFUSED",
                    !signed ? "bad signature" : !fresh ? "stale request" : "terminal not active");
            return refused();
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, display_name, role, branch_id, pin_hash, failed_attempts, locked_until, status "
                        + "FROM staff WHERE staff_number = ? FOR UPDATE", staffNumber);

        if (rows.isEmpty()) {
            pins.matches(pin, null);
            audit(tenantId, "unknown:" + staffNumber, terminalId, "REFUSED", "unknown staff number");
            return refused();
        }

        Map<String, Object> row = rows.get(0);
        String staffId = (String) row.get("id");
        int failures = ((Number) row.get("failed_attempts")).intValue();
        Timestamp lockedUntil = (Timestamp) row.get("locked_until");
        PinPolicy.StaffState state = new PinPolicy.StaffState(
                staffId,
                StaffRole.valueOf((String) row.get("role")),
                (String) row.get("branch_id"),
                "ACTIVE".equals(row.get("status")),
                failures,
                lockedUntil == null ? null : lockedUntil.toInstant());

        // Hash work happens even when the account is locked, so locked and unlocked
        // accounts cannot be told apart by how long the refusal takes.
        boolean pinMatches = pins.matches(pin, (String) row.get("pin_hash"));
        SignInOutcome outcome = policy.evaluate(state, pinMatches, terminalBranch, now);

        switch (outcome.outcome()) {
            case SIGNED_IN -> {
                jdbc.update("UPDATE staff SET failed_attempts = 0, locked_until = NULL WHERE id = ?", staffId);
                jdbc.update("UPDATE terminal SET last_seen_at = ? WHERE id = ?", Timestamp.from(now), terminalId);
                audit(tenantId, staffId, terminalId, "SIGNED_IN", null);
                return new Result(outcome, staffId, staffNumber, (String) row.get("display_name"),
                        (String) row.get("role"), (String) row.get("branch_id"));
            }
            case REJECTED -> {
                int next = failures + 1;
                Duration lock = policy.lockoutAfterFailure(next);
                jdbc.update("UPDATE staff SET failed_attempts = ?, locked_until = ? WHERE id = ?",
                        next, lock.isZero() ? null : Timestamp.from(now.plus(lock)), staffId);
                audit(tenantId, staffId, terminalId, "REFUSED", "wrong PIN, attempt " + next);
            }
            case LOCKED -> audit(tenantId, staffId, terminalId, "REFUSED", "locked");
            case NOT_ACTIVE -> audit(tenantId, staffId, terminalId, "REFUSED", "staff not active");
            case WRONG_BRANCH -> audit(tenantId, staffId, terminalId, "REFUSED", "wrong branch");
        }
        return new Result(outcome, null, null, null, null, null);
    }

    private static Result refused() {
        return new Result(SignInOutcome.refused(SignInOutcome.Result.REJECTED), null, null, null, null, null);
    }

    private static boolean verifySignature(
            String publicKey, String terminalId, String staffNumber, long epochSecond, String signatureHex) {
        try {
            byte[] signature = HexFormat.of().parseHex(signatureHex);
            return TerminalSignature.verify(publicKey, signedMessage(terminalId, staffNumber, epochSecond), signature);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void audit(String tenantId, String actor, String terminalId, String outcome, String detail) {
        jdbc.update(
                "INSERT INTO audit_log (tenant_id, actor, terminal_id, action, outcome, detail) VALUES (?, ?, ?, 'staff.signin', ?, ?)",
                tenantId, actor, terminalId, outcome, detail);
    }
}
