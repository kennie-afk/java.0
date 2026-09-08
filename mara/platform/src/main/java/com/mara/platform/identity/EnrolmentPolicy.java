package com.mara.platform.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Decides whether a terminal may enrol. Pure, so the decision can be tested exhaustively
 * without a database, and so the same rules can run at the edge if enrolment ever moves
 * closer to the branch.
 *
 * <p>The service layer is responsible for doing this inside a transaction that locks the
 * pending enrolment row, because {@link #evaluate} deciding "yes" and the row being
 * marked redeemed must be atomic. Two devices presenting the same code simultaneously
 * would otherwise both be told yes.
 */
public final class EnrolmentPolicy {

    private final Duration validity;

    public EnrolmentPolicy() {
        this(EnrolmentCode.DEFAULT_VALIDITY);
    }

    public EnrolmentPolicy(Duration validity) {
        if (validity.isZero() || validity.isNegative()) {
            throw new IllegalArgumentException("validity must be positive, got " + validity);
        }
        this.validity = validity;
    }

    /**
     * A pending enrolment as the server holds it.
     *
     * @param tenantId    who issued the code
     * @param codeHash    the stored hash; the plaintext is never persisted
     * @param issuedAt    when it was issued
     * @param redeemed    whether it has already enrolled a terminal
     * @param activeTerminals how many terminals the tenant already has
     * @param licensedTerminals how many it is entitled to
     */
    public record PendingEnrolment(
            String tenantId,
            byte[] codeHash,
            Instant issuedAt,
            boolean redeemed,
            int activeTerminals,
            int licensedTerminals) {

        public PendingEnrolment {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(codeHash, "codeHash");
            Objects.requireNonNull(issuedAt, "issuedAt");
            codeHash = codeHash.clone();
        }
    }

    /**
     * @param pending        the stored enrolment, or null when no code matched
     * @param submittedCode  what the device typed in
     * @param publicKeyBase64 the device's freshly generated Ed25519 public key
     * @param keyAlreadyKnown whether that key is already registered elsewhere
     * @param newTerminalId  the id to assign if this is accepted
     */
    public EnrolmentOutcome evaluate(
            PendingEnrolment pending,
            String submittedCode,
            String publicKeyBase64,
            boolean keyAlreadyKnown,
            Instant now,
            String newTerminalId) {

        // No matching code. Checked first so a caller cannot distinguish "no such code"
        // from any later failure by timing the response.
        if (pending == null) {
            return EnrolmentOutcome.rejected(EnrolmentOutcome.Reason.UNKNOWN_CODE);
        }

        if (!EnrolmentCode.matches(pending.codeHash(), submittedCode)) {
            return EnrolmentOutcome.rejected(EnrolmentOutcome.Reason.UNKNOWN_CODE);
        }

        if (pending.redeemed()) {
            return EnrolmentOutcome.rejected(EnrolmentOutcome.Reason.ALREADY_REDEEMED);
        }

        if (EnrolmentCode.isExpired(pending.issuedAt(), validity, now)) {
            return EnrolmentOutcome.rejected(EnrolmentOutcome.Reason.EXPIRED);
        }

        if (!TerminalSignature.isWellFormed(publicKeyBase64)) {
            return EnrolmentOutcome.rejected(EnrolmentOutcome.Reason.MALFORMED_KEY);
        }

        // A key already in use means either a cloned device or a device replaying
        // another's identity. Either way this is not a new terminal.
        if (keyAlreadyKnown) {
            return EnrolmentOutcome.rejected(EnrolmentOutcome.Reason.KEY_ALREADY_REGISTERED);
        }

        if (pending.activeTerminals() >= pending.licensedTerminals()) {
            return EnrolmentOutcome.rejected(EnrolmentOutcome.Reason.TERMINAL_LIMIT_REACHED);
        }

        return EnrolmentOutcome.accepted(newTerminalId);
    }

    public Duration validity() {
        return validity;
    }
}
