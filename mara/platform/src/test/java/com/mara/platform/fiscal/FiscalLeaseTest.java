package com.mara.platform.fiscal;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FiscalLeaseTest {

    private static final String TERMINAL = "TERM-LANE-04";
    private static final Instant NOW = Instant.parse("2026-09-06T08:00:00Z");
    private static final Instant EXPIRY = NOW.plus(Duration.ofDays(30));

    @Nested
    @DisplayName("drawing numbers offline")
    class Drawing {

        @Test
        void issuesNumbersInOrderFromTheStartOfTheLease() {
            FiscalLease lease = FiscalLease.issue(TERMINAL, 5_000L, 3L, NOW, EXPIRY);

            FiscalLease.Draw first = lease.draw().orElseThrow();
            FiscalLease.Draw second = first.remainder().draw().orElseThrow();
            FiscalLease.Draw third = second.remainder().draw().orElseThrow();

            assertEquals(5_000L, first.number());
            assertEquals(5_001L, second.number());
            assertEquals(5_002L, third.number());
            assertTrue(third.remainder().isExhausted());
        }

        @Test
        void exhaustionReturnsEmptyRatherThanThrowing() {
            // Running out of numbers must never stop the till taking money. The sale
            // proceeds as FISCAL_PENDING and is numbered when connectivity returns.
            FiscalLease lease = FiscalLease.issue(TERMINAL, 1L, 1L, NOW, EXPIRY);
            FiscalLease spent = lease.draw().orElseThrow().remainder();

            assertEquals(Optional.empty(), spent.draw());
            assertTrue(spent.isExhausted());
        }

        @Test
        void everyNumberInALeaseIsDrawnExactlyOnce() {
            FiscalLease lease = FiscalLease.issue(TERMINAL, 900L, 250L, NOW, EXPIRY);
            Set<Long> seen = new HashSet<>();

            FiscalLease current = lease;
            Optional<FiscalLease.Draw> draw;
            while ((draw = current.draw()).isPresent()) {
                assertTrue(seen.add(draw.get().number()),
                        "fiscal number %d issued twice".formatted(draw.get().number()));
                current = draw.get().remainder();
            }

            assertEquals(250, seen.size());
            assertEquals(250L, lease.size());
        }

        @Test
        void drawingDoesNotMutateTheLeaseItCameFrom() {
            FiscalLease lease = FiscalLease.issue(TERMINAL, 100L, 10L, NOW, EXPIRY);
            lease.draw().orElseThrow();

            // The original still points at its first number — a persisted lease cannot
            // drift out of step with what was actually issued.
            assertEquals(0L, lease.issuedCount());
            assertEquals(100L, lease.draw().orElseThrow().number());
        }
    }

    @Nested
    @DisplayName("leases issued by the server never overlap")
    class Disjointness {

        @Test
        void consecutiveLeasesAbutWithoutSharingANumber() {
            FiscalLease first = FiscalLease.issue("TERM-A", 1L, 5_000L, NOW, EXPIRY);
            FiscalLease second = FiscalLease.issue("TERM-B", first.lastNumber() + 1, 5_000L, NOW, EXPIRY);

            assertEquals(5_000L, first.lastNumber());
            assertEquals(5_001L, second.firstNumber());
            assertTrue(second.firstNumber() > first.lastNumber(),
                    "two terminals must never be able to issue the same fiscal number");
        }
    }

    @Nested
    @DisplayName("renewal happens while numbers remain")
    class Renewal {

        @Test
        void asksForRenewalAtTheLowWaterMark() {
            FiscalLease lease = FiscalLease.issue(TERMINAL, 1L, 100L, NOW, EXPIRY);
            assertFalse(lease.needsRenewal(NOW));

            FiscalLease current = lease;
            for (int i = 0; i < 80; i++) {
                current = current.draw().orElseThrow().remainder();
            }

            assertEquals(20L, current.remaining());
            assertTrue(current.needsRenewal(NOW), "20% remaining is the renewal threshold");
            assertFalse(current.isExhausted(), "renewal must be requested while it can still sell");
        }

        @Test
        void anExpiredLeaseNeedsRenewalEvenWithNumbersLeft() {
            FiscalLease lease = FiscalLease.issue(TERMINAL, 1L, 1_000L, NOW, EXPIRY);

            assertFalse(lease.needsRenewal(NOW));
            assertTrue(lease.needsRenewal(EXPIRY));
            assertTrue(lease.isExpired(EXPIRY), "expiry is inclusive");
        }
    }

    @Nested
    @DisplayName("unused numbers are retired, not recycled")
    class Retirement {

        @Test
        void reportsTheUnusedTailForVoiding() {
            FiscalLease lease = FiscalLease.issue(TERMINAL, 700L, 10L, NOW, EXPIRY);
            FiscalLease partly = lease.draw().orElseThrow().remainder().draw().orElseThrow().remainder();

            long[] unused = partly.unusedRange().orElseThrow();
            assertArrayEquals(new long[] {702L, 709L}, unused);
        }

        @Test
        void anExhaustedLeaseHasNothingToVoid() {
            FiscalLease lease = FiscalLease.issue(TERMINAL, 1L, 1L, NOW, EXPIRY);
            assertEquals(Optional.empty(), lease.draw().orElseThrow().remainder().unusedRange());
        }
    }

    @Nested
    @DisplayName("malformed leases are refused at construction")
    class Validation {

        @Test
        void rejectsBackwardsRange() {
            assertThrows(IllegalArgumentException.class,
                    () -> new FiscalLease(TERMINAL, 100L, 50L, 100L, NOW, EXPIRY));
        }

        @Test
        void rejectsCursorOutsideTheRange() {
            assertThrows(IllegalArgumentException.class,
                    () -> new FiscalLease(TERMINAL, 100L, 110L, 200L, NOW, EXPIRY));
        }

        @Test
        void rejectsZeroSizedLease() {
            assertThrows(IllegalArgumentException.class,
                    () -> FiscalLease.issue(TERMINAL, 1L, 0L, NOW, EXPIRY));
        }

        @Test
        void rejectsExpiryAtOrBeforeIssue() {
            assertThrows(IllegalArgumentException.class,
                    () -> FiscalLease.issue(TERMINAL, 1L, 10L, NOW, NOW));
        }

        @Test
        void rejectsFiscalNumbersBelowOne() {
            assertThrows(IllegalArgumentException.class,
                    () -> FiscalLease.issue(TERMINAL, 0L, 10L, NOW, EXPIRY));
        }
    }
}
