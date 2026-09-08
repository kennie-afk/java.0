package com.mara.platform.identity;

/**
 * The lifecycle of a terminal's right to sell.
 *
 * <p>Deliberately not a boolean. "Is this terminal allowed?" has more than two answers,
 * and flattening them loses the distinction between a till that has not finished
 * enrolling, one an owner has paused, and one that has been caught tampering — which are
 * three different conversations with three different remedies.
 */
public enum TerminalStatus {

    /** Enrolled and selling. */
    ACTIVE(true),

    /**
     * Enrolment started but the device has not yet presented its key. Cannot sell.
     * Exists so a half-finished enrolment is visible to the owner rather than silently
     * absent.
     */
    PENDING(false),

    /**
     * Temporarily stopped by the owner — a lane closed for the season, a till away for
     * repair. Reversible, and its journal is preserved.
     */
    SUSPENDED(false),

    /**
     * Permanently retired. A revoked terminal's key is never accepted again, even if the
     * same physical machine is later re-enrolled: it enrols as a new terminal with a new
     * key and a new chain, so the old journal stays sealed and attributable.
     */
    REVOKED(false);

    private final boolean maySell;

    TerminalStatus(boolean maySell) {
        this.maySell = maySell;
    }

    public boolean maySell() {
        return maySell;
    }

    /**
     * Whether uploads should still be accepted.
     *
     * <p>A suspended or revoked terminal may still be holding sales it made before the
     * status changed, and those sales are real money that belongs in the ledger.
     * Refusing them would destroy the record to punish the device. So uploads are always
     * accepted and always verified; only the right to make <em>new</em> sales is
     * withdrawn.
     */
    public boolean mayUpload() {
        return this != PENDING;
    }

    public boolean isTerminal() {
        return this == REVOKED;
    }
}
