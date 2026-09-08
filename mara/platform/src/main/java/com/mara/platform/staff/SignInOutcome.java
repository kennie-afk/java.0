package com.mara.platform.staff;

import java.time.Instant;
import java.util.Objects;

/**
 * The result of a member of staff presenting a PIN at a terminal.
 *
 * <p>As with enrolment, every refusal is the same at the terminal's screen. A cashier
 * who mistypes and a stranger guessing must see identical feedback, or the difference
 * tells the stranger whether the staff number exists.
 *
 * @param outcome    what happened
 * @param staffId    set only on success
 * @param lockedUntil set when the account is locked, for the supervisor's screen
 */
public record SignInOutcome(Result outcome, String staffId, Instant lockedUntil) {

    public SignInOutcome {
        Objects.requireNonNull(outcome, "outcome");
    }

    public static SignInOutcome success(String staffId) {
        return new SignInOutcome(Result.SIGNED_IN, Objects.requireNonNull(staffId), null);
    }

    public static SignInOutcome refused(Result result) {
        if (result == Result.SIGNED_IN) {
            throw new IllegalArgumentException("SIGNED_IN is not a refusal");
        }
        return new SignInOutcome(result, null, null);
    }

    public static SignInOutcome lockedUntil(Instant until) {
        return new SignInOutcome(Result.LOCKED, null, Objects.requireNonNull(until));
    }

    public boolean succeeded() {
        return outcome == Result.SIGNED_IN;
    }

    public enum Result {
        SIGNED_IN,
        /** No such staff number, or the PIN did not match. Deliberately one case. */
        REJECTED,
        /** Too many failures; locked for a period that grows with repetition. */
        LOCKED,
        /** Suspended or revoked by an owner. */
        NOT_ACTIVE,
        /** Correct credentials, but not for a terminal in this branch. */
        WRONG_BRANCH
    }
}
