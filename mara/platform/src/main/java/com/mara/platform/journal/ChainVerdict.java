package com.mara.platform.journal;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of verifying a batch of journal entries uploaded by one terminal.
 *
 * <p>Note what this type does <em>not</em> have: a boolean. A batch is not simply
 * valid or invalid. A terminal can deliver a perfectly well-formed run of entries
 * that is nonetheless missing sequence 4,117 — the batch verifies, and there is
 * still a hole in the record that an owner needs to see. Collapsing that into
 * "accepted: false" would either reject good sales or hide a deleted one.
 *
 * @param accepted  entries that verified and may be ingested
 * @param gaps      sequence ranges the server expected and has never received
 * @param breaks    entries whose chain or ordering did not hold
 */
public record ChainVerdict(
        List<JournalEntry> accepted,
        List<SequenceGap> gaps,
        List<ChainBreak> breaks) {

    public ChainVerdict {
        accepted = List.copyOf(Objects.requireNonNull(accepted, "accepted"));
        gaps = List.copyOf(Objects.requireNonNull(gaps, "gaps"));
        breaks = List.copyOf(Objects.requireNonNull(breaks, "breaks"));
    }

    /** True when nothing at all was wrong — safe to advance the terminal's cursor. */
    public boolean isClean() {
        return gaps.isEmpty() && breaks.isEmpty();
    }

    /**
     * True when the batch needs a human. Gaps and breaks are the two signatures of
     * a tampered journal, and both hold the terminal's reconciliation open.
     */
    public boolean needsInvestigation() {
        return !isClean();
    }

    /**
     * A run of sequence numbers the terminal skipped.
     *
     * <p>A gap is the fingerprint of a sale deleted from the till's local database:
     * removing the row removes it from the upload, but the sequence it consumed
     * cannot be reclaimed, so the hole names it.
     */
    public record SequenceGap(String terminalId, long fromSequence, long toSequence) {
        public SequenceGap {
            Objects.requireNonNull(terminalId, "terminalId");
            if (fromSequence > toSequence) {
                throw new IllegalArgumentException(
                        "gap runs backwards: %d..%d".formatted(fromSequence, toSequence));
            }
        }

        public long count() {
            return toSequence - fromSequence + 1;
        }
    }

    /** An entry that did not chain onto its predecessor, or arrived out of order. */
    public record ChainBreak(String terminalId, long sequence, Reason reason, String detail) {
        public ChainBreak {
            Objects.requireNonNull(terminalId, "terminalId");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }

        public enum Reason {
            /** previousDigest did not equal the digest of the entry before it */
            BROKEN_LINK,
            /** the same sequence number was submitted twice with different content */
            FORKED_SEQUENCE,
            /** sequence went backwards within the batch */
            OUT_OF_ORDER,
            /** claimed to be entry 1 but the server already holds an earlier chain */
            UNEXPECTED_GENESIS,
            /** the terminal's clock ran backwards across consecutive entries */
            NON_MONOTONIC_CLOCK
        }
    }
}
