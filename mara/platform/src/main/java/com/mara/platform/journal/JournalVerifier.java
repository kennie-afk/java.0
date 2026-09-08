package com.mara.platform.journal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Verifies a batch of journal entries from one terminal against what the server
 * already knows about that terminal.
 *
 * <p>This is the referee described in ARCHITECTURE.md §3. The terminal is trusted
 * to say what happened at its own counter, but not trusted to say it honestly —
 * so every claim it makes is checked against the chain head the server holds and
 * against the batch's own internal consistency.
 *
 * <p>Stateless and side-effect free by design: it takes the prior state as an
 * argument and returns a verdict. Persisting the new cursor is the caller's job,
 * inside the same transaction that ingests the sales, so a crash between
 * verification and ingest cannot advance the cursor past sales that were never
 * stored.
 */
public final class JournalVerifier {

    /**
     * What the server remembers about a terminal between batches.
     *
     * <p>{@code genesisDigest} is what distinguishes a benign resend from a chain
     * restart. Both look identical at the sequence level — a terminal re-uploading
     * its history and a terminal that wiped its database both submit sequence 1 —
     * so the only way to tell them apart is whether entry 1 is byte-for-byte the
     * entry 1 we already accepted.
     *
     * @param terminalId   the terminal
     * @param lastSequence highest sequence successfully ingested, 0 before the first
     * @param headDigest   digest of that entry, {@link ChainDigest#GENESIS} before the first
     * @param genesisDigest digest of entry 1, {@link ChainDigest#GENESIS} before the first
     * @param lastOccurredAt timestamp of that entry, for clock-monotonicity checks
     */
    public record Cursor(
            String terminalId,
            long lastSequence,
            byte[] headDigest,
            byte[] genesisDigest,
            Instant lastOccurredAt) {

        public Cursor {
            Objects.requireNonNull(terminalId, "terminalId");
            Objects.requireNonNull(headDigest, "headDigest");
            Objects.requireNonNull(genesisDigest, "genesisDigest");
            if (lastSequence < 0L) {
                throw new IllegalArgumentException("lastSequence must not be negative");
            }
            headDigest = headDigest.clone();
            genesisDigest = genesisDigest.clone();
        }

        /** The cursor for a terminal that has never uploaded anything. */
        public static Cursor fresh(String terminalId) {
            return new Cursor(terminalId, 0L, ChainDigest.GENESIS, ChainDigest.GENESIS, Instant.EPOCH);
        }

        public boolean isFresh() {
            return lastSequence == 0L;
        }
    }

    public ChainVerdict verify(Cursor cursor, List<JournalEntry> batch) {
        Objects.requireNonNull(cursor, "cursor");
        Objects.requireNonNull(batch, "batch");

        List<JournalEntry> accepted = new ArrayList<>();
        List<ChainVerdict.SequenceGap> gaps = new ArrayList<>();
        List<ChainVerdict.ChainBreak> breaks = new ArrayList<>();

        if (batch.isEmpty()) {
            return new ChainVerdict(accepted, gaps, breaks);
        }

        // Sort by sequence so an out-of-order upload — which is normal over a flaky
        // link — is not mistaken for tampering. Genuine disorder is caught below by
        // the duplicate check, not by arrival order.
        List<JournalEntry> ordered = batch.stream()
                .sorted(Comparator.comparingLong(JournalEntry::sequence))
                .toList();

        String terminalId = cursor.terminalId();
        for (JournalEntry entry : ordered) {
            if (!terminalId.equals(entry.terminalId())) {
                breaks.add(new ChainVerdict.ChainBreak(
                        terminalId, entry.sequence(), ChainVerdict.ChainBreak.Reason.OUT_OF_ORDER,
                        "entry belongs to terminal " + entry.terminalId()));
                return new ChainVerdict(List.of(), gaps, breaks);
            }
        }

        long expected = cursor.lastSequence() + 1;
        byte[] previousDigest = cursor.headDigest();
        Instant previousAt = cursor.lastOccurredAt();

        for (JournalEntry entry : ordered) {
            long sequence = entry.sequence();

            // A terminal submitting entry 1 when we already hold its chain is either
            // re-uploading history it thinks we missed, or trying to abandon history
            // it would rather we forgot. Sequence alone cannot separate those — both
            // send a 1 — so compare the entry itself against the genesis we accepted.
            //
            // Checked before the already-ingested skip below, because a restarted
            // chain submits sequences that are all <= our cursor and would otherwise
            // be waved through as an ordinary resend.
            if (entry.isGenesis() && !cursor.isFresh()) {
                if (!ChainDigest.matches(ChainDigest.of(entry), cursor.genesisDigest())) {
                    breaks.add(new ChainVerdict.ChainBreak(
                            terminalId, sequence, ChainVerdict.ChainBreak.Reason.UNEXPECTED_GENESIS,
                            "terminal restarted its chain at 1 with different content; "
                                    + "server holds up to " + cursor.lastSequence()));
                    break;
                }
                // Same genesis: an honest resend. Fall through to the skip below.
            }

            // Already ingested. A terminal that did not receive our acknowledgement
            // resends; that is expected, not an error. Skip it rather than counting
            // it twice.
            if (sequence <= cursor.lastSequence()) {
                continue;
            }

            if (sequence > expected) {
                gaps.add(new ChainVerdict.SequenceGap(terminalId, expected, sequence - 1));
                // The chain cannot be checked across a hole — we do not hold the
                // missing entry's digest — so nothing after the gap can be accepted
                // in this batch. The terminal must resend from `expected`.
                break;
            }

            if (sequence < expected) {
                breaks.add(new ChainVerdict.ChainBreak(
                        terminalId, sequence, ChainVerdict.ChainBreak.Reason.FORKED_SEQUENCE,
                        "sequence %d submitted twice within one batch".formatted(sequence)));
                break;
            }

            if (!ChainDigest.matches(entry.previousDigest(), previousDigest)) {
                breaks.add(new ChainVerdict.ChainBreak(
                        terminalId, sequence, ChainVerdict.ChainBreak.Reason.BROKEN_LINK,
                        "expected previous digest %s, got %s".formatted(
                                ChainDigest.hex(previousDigest),
                                ChainDigest.hex(entry.previousDigest()))));
                break;
            }

            // Sales cannot happen before the sale that preceded them. A backwards
            // clock is how a till back-dates a transaction into a closed shift.
            if (entry.occurredAt().isBefore(previousAt)) {
                breaks.add(new ChainVerdict.ChainBreak(
                        terminalId, sequence, ChainVerdict.ChainBreak.Reason.NON_MONOTONIC_CLOCK,
                        "occurred at %s, before previous entry at %s".formatted(
                                entry.occurredAt(), previousAt)));
                break;
            }

            accepted.add(entry);
            previousDigest = ChainDigest.of(entry);
            previousAt = entry.occurredAt();
            expected++;
        }

        return new ChainVerdict(accepted, gaps, breaks);
    }

    /**
     * The cursor the caller should persist after ingesting {@code verdict.accepted()},
     * in the same transaction.
     */
    public Cursor advance(Cursor cursor, ChainVerdict verdict) {
        List<JournalEntry> accepted = verdict.accepted();
        if (accepted.isEmpty()) {
            return cursor;
        }
        JournalEntry last = accepted.get(accepted.size() - 1);

        // Latch the genesis digest the first time entry 1 is ingested; it is what
        // every later restart claim is checked against, so it must never move.
        byte[] genesisDigest = cursor.genesisDigest();
        if (cursor.isFresh()) {
            JournalEntry first = accepted.get(0);
            if (first.isGenesis()) {
                genesisDigest = ChainDigest.of(first);
            }
        }

        return new Cursor(
                cursor.terminalId(),
                last.sequence(),
                ChainDigest.of(last),
                genesisDigest,
                last.occurredAt());
    }
}
