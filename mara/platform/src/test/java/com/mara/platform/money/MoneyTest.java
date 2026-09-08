package com.mara.platform.money;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency KES = Currency.getInstance("KES");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency JPY = Currency.getInstance("JPY");

    @Nested
    @DisplayName("allocation never loses or invents a minor unit")
    class Allocation {

        @Test
        void distributesRemainderAcrossLeadingParts() {
            Money[] parts = Money.of(100, KES).allocate(3);
            assertArrayEquals(new long[] {34, 33, 33}, minorsOf(parts));
        }

        @Test
        void everySplitSumsBackToTheOriginal() {
            // The property that matters: whatever the split, the bill still balances.
            for (long amount = 0; amount <= 500; amount++) {
                for (int parts = 1; parts <= 9; parts++) {
                    Money original = Money.of(amount, KES);
                    Money rebuilt = Money.sum(KES, Arrays.asList(original.allocate(parts)));
                    assertEquals(original, rebuilt,
                            "allocating %d into %d parts lost or invented a cent".formatted(amount, parts));
                }
            }
        }

        @Test
        void handlesNegativeAmountsSymmetrically() {
            Money[] parts = Money.of(-100, KES).allocate(3);
            assertArrayEquals(new long[] {-34, -33, -33}, minorsOf(parts));
            assertEquals(Money.of(-100, KES), Money.sum(KES, Arrays.asList(parts)));
        }

        @Test
        void rejectsNonPositivePartCounts() {
            assertThrows(IllegalArgumentException.class, () -> Money.of(100, KES).allocate(0));
            assertThrows(IllegalArgumentException.class, () -> Money.of(100, KES).allocate(-1));
        }

        private long[] minorsOf(Money[] parts) {
            return Arrays.stream(parts).mapToLong(Money::minor).toArray();
        }
    }

    @Nested
    @DisplayName("tax is computed on exact integers")
    class Percentage {

        @Test
        void appliesKenyanStandardVatRate() {
            // 16% of KES 1,000.00 is exactly KES 160.00
            assertEquals(Money.of(16_000, KES), Money.of(100_000, KES).percentage(1_600));
        }

        @Test
        void roundsHalfUpAwayFromZero() {
            // 16% of 3 minor units is 0.48 -> 0
            assertEquals(Money.of(0, KES), Money.of(3, KES).percentage(1_600));
            // 50% of 5 minor units is 2.5 -> 3
            assertEquals(Money.of(3, KES), Money.of(5, KES).percentage(5_000));
            // and symmetrically for a refund
            assertEquals(Money.of(-3, KES), Money.of(-5, KES).percentage(5_000));
        }

        @Test
        void rejectsNegativeRates() {
            assertThrows(IllegalArgumentException.class, () -> Money.of(100, KES).percentage(-1));
        }
    }

    @Nested
    @DisplayName("currencies never silently mix")
    class Currencies {

        @Test
        void refusesToAddDifferentCurrencies() {
            CurrencyMismatchException thrown = assertThrows(
                    CurrencyMismatchException.class,
                    () -> Money.of(100, KES).plus(Money.of(100, USD)));
            assertEquals(KES, thrown.expected());
            assertEquals(USD, thrown.actual());
        }

        @Test
        void refusesToCompareDifferentCurrencies() {
            assertThrows(CurrencyMismatchException.class,
                    () -> Money.of(100, KES).compareTo(Money.of(100, USD)));
        }
    }

    @Nested
    @DisplayName("overflow is fatal rather than silent")
    class Overflow {

        @Test
        void additionOverflowThrows() {
            assertThrows(ArithmeticException.class,
                    () -> Money.of(Long.MAX_VALUE, KES).plus(Money.of(1, KES)));
        }

        @Test
        void multiplicationOverflowThrows() {
            assertThrows(ArithmeticException.class,
                    () -> Money.of(Long.MAX_VALUE, KES).times(2));
        }
    }

    @Nested
    @DisplayName("rendering follows the currency's own scale")
    class Rendering {

        @Test
        void twoDecimalCurrency() {
            assertEquals("KES 1234.56", Money.of(123_456, KES).toString());
        }

        @Test
        void zeroDecimalCurrency() {
            assertEquals("JPY 1234", Money.of(1_234, JPY).toString());
        }

        @Test
        void keepsSignVisibleBelowOneUnit() {
            assertEquals("KES -0.45", Money.of(-45, KES).toString());
        }
    }

    @Test
    void sumsAnEmptyBillToZero() {
        assertEquals(Money.zero(KES), Money.sum(KES, List.of()));
    }
}
