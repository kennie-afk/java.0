package com.mara.platform.staff;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Decides whether a member of staff may sign in at a terminal.
 *
 * <p>A four-digit PIN has ten thousand possibilities. Argon2id makes each <em>offline</em>
 * guess expensive, but the realistic attack is not offline — it is somebody standing at
 * the till after closing, typing. Against that, hashing cost is irrelevant and only two
 * things help: a small number of attempts, and a lockout that grows.
 *
 * <p>So the lockout doubles: 1 minute, 2, 4, 8, up to a cap. Five attempts costs
 * seconds; fifty costs longer than a night shift. That turns exhaustive guessing from
 * a patient evening's work into something that cannot be finished before the shop
 * reopens and the count is done.
 *
 * <p>Pure, and takes the clock as an argument, so lockout behaviour can be tested
 * exhaustively without sleeping.
 */
public final class PinPolicy {

    /** Failures tolerated before the first lockout. */
    public static final int ATTEMPTS_BEFORE_LOCKOUT = 5;

    private static final Duration BASE_LOCKOUT = Duration.ofMinutes(1);

    /**
     * Ceiling on the lockout. Long enough to defeat guessing, short enough that a
     * cashier who genuinely forgot is not stranded for a whole shift — an unreachable
     * lockout gets solved by sharing a supervisor's PIN, which is worse than the attack.
     */
    private static final Duration MAX_LOCKOUT = Duration.ofMinutes(30);

    /**
     * The state the server holds for one member of staff.
     *
     * @param staffId        who
     * @param role           their role
     * @param branchId       the branch they belong to; null for an owner
     * @param active         false when suspended or revoked
     * @param failedAttempts consecutive failures since the last success
     * @param lockedUntil    when the current lockout expires, or null
     */
    public record StaffState(
            String staffId,
            StaffRole role,
            String branchId,
            boolean active,
            int failedAttempts,
            Instant lockedUntil) {

        public StaffState {
            Objects.requireNonNull(staffId, "staffId");
            Objects.requireNonNull(role, "role");
            if (failedAttempts < 0) {
                throw new IllegalArgumentException("failedAttempts must not be negative");
            }
        }
    }

    /**
     * @param staff         the stored state, or null when the staff number is unknown
     * @param pinMatches    whether the presented PIN verified against the stored hash
     * @param terminalBranch the branch of the terminal being signed in at
     */
    public SignInOutcome evaluate(
            StaffState staff, boolean pinMatches, String terminalBranch, Instant now) {

        // Unknown staff number. Indistinguishable from a wrong PIN by design: telling
        // an attacker which staff numbers exist halves their work before they start.
        if (staff == null) {
            return SignInOutcome.refused(SignInOutcome.Result.REJECTED);
        }

        // Lockout is checked before the PIN. Otherwise a locked account still answers
        // "was that the right PIN?", and the lockout protects nothing.
        if (isLocked(staff, now)) {
            return SignInOutcome.lockedUntil(staff.lockedUntil());
        }

        if (!staff.active()) {
            return SignInOutcome.refused(SignInOutcome.Result.NOT_ACTIVE);
        }

        if (!pinMatches) {
            return SignInOutcome.refused(SignInOutcome.Result.REJECTED);
        }

        // Correct PIN, wrong place. A cashier's credentials work at their own branch and
        // nowhere else, so a PIN learned by watching over a shoulder in one shop cannot
        // be used in another.
        if (!staff.role().spansBranches() && !Objects.equals(staff.branchId(), terminalBranch)) {
            return SignInOutcome.refused(SignInOutcome.Result.WRONG_BRANCH);
        }

        return SignInOutcome.success(staff.staffId());
    }

    public boolean isLocked(StaffState staff, Instant now) {
        return staff.lockedUntil() != null && now.isBefore(staff.lockedUntil());
    }

    /**
     * The lockout to apply after a failure, given how many have now accumulated.
     *
     * <p>Returns empty while the account is still within its allowance, so early
     * mistypes cost nothing.
     */
    public Duration lockoutAfterFailure(int failedAttempts) {
        if (failedAttempts < ATTEMPTS_BEFORE_LOCKOUT) {
            return Duration.ZERO;
        }
        int doublings = failedAttempts - ATTEMPTS_BEFORE_LOCKOUT;
        // Cap the shift before it overflows; 30 doublings already exceeds any cap.
        if (doublings >= 30) {
            return MAX_LOCKOUT;
        }
        Duration scaled = BASE_LOCKOUT.multipliedBy(1L << doublings);
        return scaled.compareTo(MAX_LOCKOUT) > 0 ? MAX_LOCKOUT : scaled;
    }

    /**
     * Whether {@code authoriser} may approve {@code action} for a sale at
     * {@code terminalBranch}.
     *
     * <p>Separate from sign-in because the authoriser is usually not the person signed
     * in: a cashier calls a supervisor over, who authorises on the cashier's terminal.
     * Both identities end up on the audit record, which is the entire point.
     */
    public boolean mayAuthorise(StaffState authoriser, ElevatedAction action, String terminalBranch, Instant now) {
        if (authoriser == null || !authoriser.active() || isLocked(authoriser, now)) {
            return false;
        }
        if (!authoriser.role().spansBranches()
                && !Objects.equals(authoriser.branchId(), terminalBranch)) {
            return false;
        }
        return authoriser.role().canAuthorise(action);
    }
}
