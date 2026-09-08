package com.mara.platform.money;

import java.util.Currency;

/**
 * Thrown when two amounts in different currencies are combined.
 *
 * <p>Unchecked and deliberately fatal: a tab that has accumulated both KES and USD
 * lines is already corrupt, and continuing would post a meaningless total to the
 * ledger.
 */
public class CurrencyMismatchException extends RuntimeException {

    private final transient Currency expected;
    private final transient Currency actual;

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("cannot combine %s with %s".formatted(expected.getCurrencyCode(), actual.getCurrencyCode()));
        this.expected = expected;
        this.actual = actual;
    }

    public Currency expected() {
        return expected;
    }

    public Currency actual() {
        return actual;
    }
}
