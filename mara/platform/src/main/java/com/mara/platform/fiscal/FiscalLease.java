package com.mara.platform.fiscal;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A contiguous block of fiscal invoice numbers leased to one terminal.
 *
 * <p>The problem this solves is set out in ARCHITECTURE.md §2.4. A tax invoice needs a
 * number from a gapless, authority-recognised sequence. If the terminal invents one it
 * may collide with another terminal's; if it waits for the server it cannot sell
 * offline. Neither is acceptable, so the server hands out disjoint ranges in advance and
 * the terminal draws from its own range while partitioned. Uniqueness holds by
 * construction: no two leases overlap.
 *
 * <p>This type is immutable. {@link #draw()} returns a new lease alongside the number
 * taken, so a lease can be persisted, replayed and reasoned about without a mutable
 * cursor drifting out of step with what was actually issued.
 */
public record FiscalLease(
        String terminalId,
        long firstNumber,
        long lastNumber,
        long nextNumber,
        Instant issuedAt,
        Instant expiresAt) {

    /**
     * Remaining fraction below which the terminal should renew eagerly. At 20% a
     * shop that is online for ten minutes a day never runs dry, while the server is
     * not handing out ranges it will mostly have to void.
     */
    public static final double RENEWAL_THRESHOLD = 0.20;

    public FiscalLease {
        Objects.requireNonNull(terminalId, "terminalId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (terminalId.isBlank()) {
            throw new IllegalArgumentException("terminalId must not be blank");
        }
        if (firstNumber < 1L) {
            throw new IllegalArgumentException("fiscal numbers start at 1, got " + firstNumber);
        }
        if (lastNumber < firstNumber) {
            throw new IllegalArgumentException(
                    "lease runs backwards: %d..%d".formatted(firstNumber, lastNumber));
        }
        // nextNumber may sit one past lastNumber, which is how exhaustion is represented.
        if (nextNumber < firstNumber || nextNumber > lastNumber + 1) {
            throw new IllegalArgumentException(
                    "nextNumber %d outside lease %d..%d".formatted(nextNumber, firstNumber, lastNumber));
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("lease expires at or before it was issued");
        }
    }

    public static FiscalLease issue(
            String terminalId, long firstNumber, long size, Instant issuedAt, Instant expiresAt) {
        if (size < 1L) {
            throw new IllegalArgumentException("lease size must be positive, got " + size);
        }
        long lastNumber = Math.addExact(firstNumber, size - 1);
        return new FiscalLease(terminalId, firstNumber, lastNumber, firstNumber, issuedAt, expiresAt);
    }

    public long size() {
        return lastNumber - firstNumber + 1;
    }

    public long issuedCount() {
        return nextNumber - firstNumber;
    }

    public long remaining() {
        return lastNumber - nextNumber + 1;
    }

    public boolean isExhausted() {
        return nextNumber > lastNumber;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * Whether the terminal should ask for a replacement lease now, while it still has
     * numbers left to sell with.
     */
    public boolean needsRenewal(Instant now) {
        return isExhausted()
                || isExpired(now)
                || (double) remaining() / size() <= RENEWAL_THRESHOLD;
    }

    /**
     * Takes the next fiscal number, if one is left.
     *
     * <p>Returns empty rather than throwing when exhausted. Running out of numbers must
     * not stop the till selling — the sale proceeds as {@code FISCAL_PENDING} and is
     * numbered when connectivity returns. Degradation is graded, not binary.
     */
    public Optional<Draw> draw() {
        if (isExhausted()) {
            return Optional.empty();
        }
        return Optional.of(new Draw(
                nextNumber,
                new FiscalLease(terminalId, firstNumber, lastNumber, nextNumber + 1, issuedAt, expiresAt)));
    }

    /** A number taken, and the lease as it now stands. */
    public record Draw(long number, FiscalLease remainder) {
    }

    /**
     * The unused tail, for the server to void when this lease is returned.
     *
     * <p>Unused numbers are retired rather than recycled: a fiscal number issued twice
     * is a far worse defect than one never used, because two different sales would
     * carry the same legal identifier.
     */
    public Optional<long[]> unusedRange() {
        if (isExhausted()) {
            return Optional.empty();
        }
        return Optional.of(new long[] {nextNumber, lastNumber});
    }
}
