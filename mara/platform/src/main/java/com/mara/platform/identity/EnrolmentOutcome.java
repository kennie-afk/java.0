package com.mara.platform.identity;

import java.util.Objects;

/**
 * The result of a terminal presenting an enrolment code and a public key.
 *
 * <p>Every rejection collapses to the same thing at the API boundary. The caller learns
 * that enrolment failed and nothing else — not whether the code was wrong, expired or
 * already spent — because those three answers together are a search procedure. The
 * {@link Reason} exists for the audit log and the owner's screen, both of which are
 * behind authentication the attacker does not have.
 */
public record EnrolmentOutcome(boolean accepted, Reason reason, String terminalId) {

    public EnrolmentOutcome {
        Objects.requireNonNull(reason, "reason");
    }

    public static EnrolmentOutcome accepted(String terminalId) {
        return new EnrolmentOutcome(true, Reason.ENROLLED, Objects.requireNonNull(terminalId));
    }

    public static EnrolmentOutcome rejected(Reason reason) {
        if (reason == Reason.ENROLLED) {
            throw new IllegalArgumentException("ENROLLED is not a rejection");
        }
        return new EnrolmentOutcome(false, reason, null);
    }

    public enum Reason {
        ENROLLED,
        /** No such code for this tenant, or the code did not match. */
        UNKNOWN_CODE,
        /** Past its validity window. */
        EXPIRED,
        /** Already used to enrol a terminal. */
        ALREADY_REDEEMED,
        /** The submitted public key was not a well-formed Ed25519 key. */
        MALFORMED_KEY,
        /** The submitted key is already registered to another terminal. */
        KEY_ALREADY_REGISTERED,
        /** The tenant has reached its licensed terminal count. */
        TERMINAL_LIMIT_REACHED
    }
}
