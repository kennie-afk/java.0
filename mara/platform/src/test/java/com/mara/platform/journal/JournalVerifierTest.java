package com.mara.platform.journal;

import static org.junit.jupiter.api.Assertions.*;

import com.mara.platform.journal.ChainVerdict.ChainBreak;
import com.mara.platform.journal.JournalVerifier.Cursor;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * These tests are written from the attacker's side. A journal that only ever sees
 * honest input proves nothing — the question is whether a till that has been edited
 * by the person it is meant to audit can get its story past the server.
 */
class JournalVerifierTest {

    private static final String TERMINAL = "TERM-LANE-04";
    private static final Instant T0 = Instant.parse("2026-09-06T08:00:00Z");

    private final JournalVerifier verifier = new JournalVerifier();

    @Nested
    @DisplayName("an honest terminal")
    class Honest {

        @Test
        void firstBatchFromAFreshTerminalIsAccepted() {
            List<JournalEntry> batch = chain(Cursor.fresh(TERMINAL), 5);

            ChainVerdict verdict = verifier.verify(Cursor.fresh(TERMINAL), batch);

            assertTrue(verdict.isClean());
            assertEquals(5, verdict.accepted().size());
        }

        @Test
        void resendingAlreadyIngestedEntriesIsNotAnError() {
            // A terminal that never received our acknowledgement resends. That is a
            // flaky link, not fraud, and must not be counted twice.
            List<JournalEntry> batch = chain(Cursor.fresh(TERMINAL), 5);
            Cursor after = verifier.advance(Cursor.fresh(TERMINAL),
                    verifier.verify(Cursor.fresh(TERMINAL), batch));

            ChainVerdict replay = verifier.verify(after, batch);

            assertTrue(replay.isClean());
            assertTrue(replay.accepted().isEmpty(), "resent entries must not be ingested again");
        }

        @Test
        void entriesArrivingOutOfOrderAreStillAccepted() {
            List<JournalEntry> batch = new ArrayList<>(chain(Cursor.fresh(TERMINAL), 4));
            java.util.Collections.reverse(batch);

            ChainVerdict verdict = verifier.verify(Cursor.fresh(TERMINAL), batch);

            assertTrue(verdict.isClean(), "arrival order is a transport detail, not tampering");
            assertEquals(4, verdict.accepted().size());
        }

        @Test
        void continuesCleanlyAcrossBatches() {
            Cursor cursor = Cursor.fresh(TERMINAL);
            List<JournalEntry> first = chain(cursor, 3);
            cursor = verifier.advance(cursor, verifier.verify(cursor, first));

            List<JournalEntry> second = chain(cursor, 3);
            ChainVerdict verdict = verifier.verify(cursor, second);

            assertTrue(verdict.isClean());
            assertEquals(4L, verdict.accepted().get(0).sequence());
        }
    }

    @Nested
    @DisplayName("a tampered terminal")
    class Tampered {

        @Test
        void deletingASaleLeavesADetectableHole() {
            // The cashier voids sale 3 out of the local database and uploads the rest.
            List<JournalEntry> full = chain(Cursor.fresh(TERMINAL), 5);
            List<JournalEntry> doctored = new ArrayList<>(full);
            doctored.remove(2);   // sequence 3

            ChainVerdict verdict = verifier.verify(Cursor.fresh(TERMINAL), doctored);

            assertTrue(verdict.needsInvestigation());
            assertEquals(1, verdict.gaps().size());
            ChainVerdict.SequenceGap gap = verdict.gaps().get(0);
            assertEquals(3L, gap.fromSequence());
            assertEquals(3L, gap.toSequence());
            assertEquals(1L, gap.count());
            // Sequences 1 and 2 are still good and must not be rejected with it.
            assertEquals(2, verdict.accepted().size());
        }

        @Test
        void editingAHistoricSaleBreaksTheChain() {
            // Rewrite the body of sale 2 — reducing its total — but leave the rest.
            List<JournalEntry> batch = new ArrayList<>(chain(Cursor.fresh(TERMINAL), 4));
            JournalEntry original = batch.get(1);
            batch.set(1, new JournalEntry(
                    original.terminalId(),
                    original.sequence(),
                    original.occurredAt(),
                    ChainDigest.body(ChainDigest.utf8("total"), ChainDigest.longBytes(1L)),
                    original.previousDigest()));

            ChainVerdict verdict = verifier.verify(Cursor.fresh(TERMINAL), batch);

            assertTrue(verdict.needsInvestigation());
            assertEquals(1, verdict.breaks().size());
            ChainBreak broken = verdict.breaks().get(0);
            // Entry 2 itself still links correctly; entry 3 is where the forgery shows,
            // because its previousDigest commits to the *original* entry 2.
            assertEquals(3L, broken.sequence());
            assertEquals(ChainBreak.Reason.BROKEN_LINK, broken.reason());
        }

        @Test
        void restartingTheChainToAbandonHistoryIsRejected() {
            Cursor established = new Cursor(TERMINAL, 42L,
                    ChainDigest.body(ChainDigest.utf8("head")),
                    ChainDigest.body(ChainDigest.utf8("the genesis we actually accepted")),
                    T0);
            List<JournalEntry> freshStart = chain(Cursor.fresh(TERMINAL), 2);

            ChainVerdict verdict = verifier.verify(established, freshStart);

            assertEquals(1, verdict.breaks().size());
            assertEquals(ChainBreak.Reason.UNEXPECTED_GENESIS, verdict.breaks().get(0).reason());
            assertTrue(verdict.accepted().isEmpty());
        }

        @Test
        void backDatingASaleIntoAClosedShiftIsRejected() {
            Cursor cursor = Cursor.fresh(TERMINAL);
            List<JournalEntry> batch = new ArrayList<>(chain(cursor, 2));
            JournalEntry second = batch.get(1);
            // Same chain links, but the clock runs backwards — the signature of a sale
            // moved into yesterday's takings.
            batch.set(1, new JournalEntry(
                    second.terminalId(),
                    second.sequence(),
                    T0.minus(Duration.ofHours(6)),
                    second.bodyDigest(),
                    second.previousDigest()));

            ChainVerdict verdict = verifier.verify(cursor, batch);

            assertEquals(1, verdict.breaks().size());
            assertEquals(ChainBreak.Reason.NON_MONOTONIC_CLOCK, verdict.breaks().get(0).reason());
        }

        @Test
        void submittingTwoDifferentSalesUnderOneSequenceIsRejected() {
            Cursor cursor = Cursor.fresh(TERMINAL);
            List<JournalEntry> batch = new ArrayList<>(chain(cursor, 2));
            JournalEntry fork = new JournalEntry(
                    TERMINAL, 2L, T0.plusSeconds(90),
                    ChainDigest.body(ChainDigest.utf8("a different sale")),
                    ChainDigest.of(batch.get(0)));
            batch.add(fork);

            ChainVerdict verdict = verifier.verify(cursor, batch);

            assertTrue(verdict.needsInvestigation());
            assertEquals(ChainBreak.Reason.FORKED_SEQUENCE, verdict.breaks().get(0).reason());
        }

        @Test
        void aResendAndARestartAreDistinguishedByContentNotBySequence() {
            // Both submit sequence 1 against a non-fresh cursor. Only one is an attack,
            // and telling them apart is the whole reason the cursor remembers genesis.
            Cursor fresh = Cursor.fresh(TERMINAL);
            List<JournalEntry> original = chain(fresh, 3);
            Cursor after = verifier.advance(fresh, verifier.verify(fresh, original));

            // Honest: the same three entries, re-uploaded.
            ChainVerdict resend = verifier.verify(after, original);
            assertTrue(resend.isClean(), "an identical resend is not tampering");

            // Hostile: a wiped till starting a new chain at 1 with different sales.
            List<JournalEntry> rewritten = new ArrayList<>();
            byte[] previous = ChainDigest.GENESIS;
            for (long sequence = 1; sequence <= 3; sequence++) {
                JournalEntry entry = new JournalEntry(TERMINAL, sequence, T0.plusSeconds(sequence * 60),
                        ChainDigest.body(ChainDigest.utf8("a quieter day"), ChainDigest.longBytes(sequence)),
                        previous);
                rewritten.add(entry);
                previous = ChainDigest.of(entry);
            }

            ChainVerdict restart = verifier.verify(after, rewritten);
            assertEquals(1, restart.breaks().size());
            assertEquals(ChainBreak.Reason.UNEXPECTED_GENESIS, restart.breaks().get(0).reason());
            assertTrue(restart.accepted().isEmpty());
        }

        @Test
        void entriesFromAnotherTerminalAreRefusedWholesale() {
            List<JournalEntry> batch = new ArrayList<>(chain(Cursor.fresh(TERMINAL), 2));
            batch.add(new JournalEntry("TERM-LANE-09", 3L, T0.plusSeconds(200),
                    ChainDigest.body(ChainDigest.utf8("x")), ChainDigest.of(batch.get(1))));

            ChainVerdict verdict = verifier.verify(Cursor.fresh(TERMINAL), batch);

            assertTrue(verdict.accepted().isEmpty(),
                    "a batch mixing terminals is rejected entirely rather than partly trusted");
            assertFalse(verdict.breaks().isEmpty());
        }
    }

    @Nested
    @DisplayName("cursor advancement")
    class Advancement {

        @Test
        void advancesOnlyOverAcceptedEntries() {
            List<JournalEntry> full = chain(Cursor.fresh(TERMINAL), 5);
            List<JournalEntry> doctored = new ArrayList<>(full);
            doctored.remove(2);

            ChainVerdict verdict = verifier.verify(Cursor.fresh(TERMINAL), doctored);
            Cursor next = verifier.advance(Cursor.fresh(TERMINAL), verdict);

            // Must stop at 2 — not skip to 5 — so the terminal is asked to resend from 3.
            assertEquals(2L, next.lastSequence());
        }

        @Test
        void emptyBatchLeavesTheCursorUntouched() {
            Cursor cursor = new Cursor(TERMINAL, 7L,
                    ChainDigest.body(ChainDigest.utf8("h")),
                    ChainDigest.body(ChainDigest.utf8("g")),
                    T0);
            ChainVerdict verdict = verifier.verify(cursor, List.of());
            assertEquals(7L, verifier.advance(cursor, verdict).lastSequence());
        }
    }

    /** Builds a well-formed run of entries continuing from {@code cursor}. */
    private static List<JournalEntry> chain(Cursor cursor, int count) {
        List<JournalEntry> entries = new ArrayList<>(count);
        byte[] previous = cursor.headDigest();
        long sequence = cursor.lastSequence() + 1;
        Instant at = T0.plusSeconds(cursor.lastSequence() * 60L);

        for (int i = 0; i < count; i++) {
            JournalEntry entry = new JournalEntry(
                    cursor.terminalId(),
                    sequence,
                    at,
                    ChainDigest.body(
                            ChainDigest.utf8("sale"),
                            ChainDigest.longBytes(sequence),
                            ChainDigest.longBytes(sequence * 1_250L)),
                    previous);
            entries.add(entry);
            previous = ChainDigest.of(entry);
            sequence++;
            at = at.plusSeconds(60);
        }
        return entries;
    }
}
