package com.mara.platform.journal;

import java.time.Instant;
import java.util.Objects;

/**
 * One recorded sale as the terminal committed it, before the server has seen it.
 *
 * <p>Everything here is part of the signed, chained payload. {@code bodyDigest} is
 * the digest of the sale's own content — lines, tenders, total — computed by the
 * caller from a canonical encoding; this type deliberately does not know how a
 * sale is shaped, so the chain can outlive changes to the sale model.
 *
 * @param terminalId  which counter recorded it
 * @param sequence    strictly monotonic per terminal, starting at 1, never reused
 * @param occurredAt  when the terminal says it happened, in its own clock
 * @param bodyDigest  digest of the canonical sale body
 * @param previousDigest digest of entry {@code sequence - 1}; the genesis entry
 *                       carries {@link ChainDigest#GENESIS}
 */
public record JournalEntry(
        String terminalId,
        long sequence,
        Instant occurredAt,
        byte[] bodyDigest,
        byte[] previousDigest) {

    public JournalEntry {
        Objects.requireNonNull(terminalId, "terminalId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(bodyDigest, "bodyDigest");
        Objects.requireNonNull(previousDigest, "previousDigest");
        if (terminalId.isBlank()) {
            throw new IllegalArgumentException("terminalId must not be blank");
        }
        if (sequence < 1L) {
            throw new IllegalArgumentException("sequence starts at 1, got " + sequence);
        }
        // Defensive copies: a caller must not be able to mutate an entry's digests
        // after it has been chained.
        bodyDigest = bodyDigest.clone();
        previousDigest = previousDigest.clone();
    }

    public boolean isGenesis() {
        return sequence == 1L;
    }
}
