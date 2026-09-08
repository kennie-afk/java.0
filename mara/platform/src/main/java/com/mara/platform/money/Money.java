package com.mara.platform.money;

import java.util.Currency;
import java.util.Objects;

/**
 * An amount in minor units of a single currency — 1234 KES is twelve shillings
 * thirty-four cents.
 *
 * <p>Money is never a {@code double} anywhere in this system. A till that adds
 * 0.1 + 0.2 and prints 0.30000000000000004 on a receipt is not a rounding
 * curiosity, it is a tax record that does not reconcile. Minor units keep every
 * intermediate value exact, and the only place rounding is permitted is
 * {@link #allocate}, which is written so that rounding cannot lose or invent a
 * cent.
 *
 * <p>The scale of the minor unit comes from {@link Currency}, so KES and USD get
 * two decimal places, JPY gets none and TND gets three, without this class
 * knowing anything about individual currencies.
 */
public record Money(long minor, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long minor, Currency currency) {
        return new Money(minor, currency);
    }

    public static Money of(long minor, String currencyCode) {
        return new Money(minor, Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(0L, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minor, other.minor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minor, other.minor), currency);
    }

    public Money negated() {
        return new Money(Math.negateExact(minor), currency);
    }

    public Money times(long factor) {
        return new Money(Math.multiplyExact(minor, factor), currency);
    }

    public boolean isZero() {
        return minor == 0L;
    }

    public boolean isNegative() {
        return minor < 0L;
    }

    public boolean isPositive() {
        return minor > 0L;
    }

    /**
     * Splits this amount into {@code parts} pieces that sum back to exactly this
     * amount.
     *
     * <p>The remainder is distributed one minor unit at a time across the leading
     * parts, so 100 split three ways is 34, 33, 33 — not 33, 33, 33 with a cent
     * quietly discarded. Callers that split a bill across diners, or VAT across
     * lines, depend on the total surviving the split.
     */
    public Money[] allocate(int parts) {
        if (parts < 1) {
            throw new IllegalArgumentException("cannot allocate into " + parts + " parts");
        }
        long base = minor / parts;
        long remainder = minor - base * parts;   // carries the sign of `minor`
        long step = remainder < 0 ? -1 : 1;

        Money[] out = new Money[parts];
        for (int i = 0; i < parts; i++) {
            long extra = Math.abs(remainder) > i ? step : 0L;
            out[i] = new Money(base + extra, currency);
        }
        return out;
    }

    /**
     * Applies a rate expressed in basis points — 1600 bp is 16%, Kenya's standard
     * VAT rate — using half-up rounding on the exact integer product.
     *
     * <p>Basis points rather than a {@code BigDecimal} percentage because tax
     * rates are always expressible this way, and integers cannot drift.
     */
    public Money percentage(long basisPoints) {
        if (basisPoints < 0) {
            throw new IllegalArgumentException("basis points must not be negative: " + basisPoints);
        }
        long numerator = Math.multiplyExact(minor, basisPoints);
        long rounded = roundHalfUp(numerator, 10_000L);
        return new Money(rounded, currency);
    }

    /** Half-up division that rounds away from zero on a tie, symmetrically for negatives. */
    private static long roundHalfUp(long numerator, long denominator) {
        long quotient = numerator / denominator;
        long remainder = numerator % denominator;
        if (Math.abs(remainder) * 2 >= denominator) {
            quotient += (numerator < 0) ? -1 : 1;
        }
        return quotient;
    }

    public static Money sum(Currency currency, Iterable<Money> amounts) {
        Money total = zero(currency);
        for (Money amount : amounts) {
            total = total.plus(amount);
        }
        return total;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(minor, other.minor);
    }

    /** Renders for humans and receipts: {@code KES 1234.56}. Never used for arithmetic. */
    @Override
    public String toString() {
        int digits = Math.max(currency.getDefaultFractionDigits(), 0);
        if (digits == 0) {
            return currency.getCurrencyCode() + " " + minor;
        }
        long scale = (long) Math.pow(10, digits);
        long units = minor / scale;
        long fraction = Math.abs(minor % scale);
        String sign = (minor < 0 && units == 0) ? "-" : "";
        return "%s %s%d.%0{digits}d".replace("{digits}", String.valueOf(digits))
                .formatted(currency.getCurrencyCode(), sign, units, fraction);
    }
}
