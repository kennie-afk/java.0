package com.mara.platform.sale;

import com.mara.platform.journal.ChainDigest;
import java.util.List;
import java.util.function.Function;

/**
 * The terminal's canonical encoding of a sale, reproduced so the server can recompute the
 * body digest a terminal signed instead of trusting the digest it sent.
 *
 * <p>{@link ChainDigest#body} deliberately defines no sale shape; the field list is the
 * terminal's contract ({@code saleBodyDigest} in apps/terminal/src/lib/sale.ts):
 * version, currency, canonical lines, canonical payments, total as an 8-byte integer,
 * canonical fiscal, and (v2 only) the canonical cashier. Lines, payments and cashier are
 * the output of JavaScript's {@code JSON.stringify} over arrays of strings, so
 * {@link #jsonString} matches its escaping rule exactly. Equality with the terminal is not
 * assumed: it is pinned by a fixture the terminal's own code produced
 * (SaleCanonicalTest).
 */
public final class SaleCanonical {

    private SaleCanonical() {
    }

    public static String lines(SaleBody body) {
        return array(body.lines(), l -> array(List.of(
                l.sku(), l.name(), String.valueOf(l.qty()), l.unitMinor(),
                String.valueOf(l.taxBp()), l.netMinor(), l.taxMinor()), Function.identity()));
    }

    public static String payments(SaleBody body) {
        return array(body.payments(), p -> array(List.of(
                p.method(), p.appliedMinor(), p.reference(), p.tenderedMinor()), Function.identity()));
    }

    public static String fiscal(SaleBody body) {
        return body.fiscal().status() + ":" + body.fiscal().number();
    }

    public static String cashier(SaleBody body) {
        SaleBody.Cashier c = body.cashier();
        return c == null ? "" : array(List.of(c.staffId(), c.staffNumber(), c.name()), Function.identity());
    }

    /** Digest of the sale body, as the terminal computes it. */
    public static byte[] bodyDigest(SaleBody body) {
        boolean v2 = SaleBody.V2.equals(body.version());
        byte[] version = ChainDigest.utf8(body.version());
        byte[] currency = ChainDigest.utf8(body.currency());
        byte[] lines = ChainDigest.utf8(lines(body));
        byte[] payments = ChainDigest.utf8(payments(body));
        byte[] total = ChainDigest.longBytes(Long.parseLong(body.totalMinor()));
        byte[] fiscal = ChainDigest.utf8(fiscal(body));
        return v2
                ? ChainDigest.body(version, currency, lines, payments, total, fiscal,
                        ChainDigest.utf8(cashier(body)))
                : ChainDigest.body(version, currency, lines, payments, total, fiscal);
    }

    private static <T> String array(List<T> items, Function<T, String> render) {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (T item : items) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(item instanceof String s ? jsonString(s) : render.apply(item));
        }
        return out.append(']').toString();
    }

    /**
     * JSON.stringify's string encoding: quote, backslash and C0 controls are escaped
     * (short forms for backspace, tab, newline, form feed and carriage return, otherwise a
     * lower-case four-digit hex escape), everything else is emitted as is, and a lone
     * surrogate is escaped the same way.
     */
    static String jsonString(String s) {
        StringBuilder out = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\f' -> out.append("\\f");
                case '\r' -> out.append("\\r");
                default -> {
                    boolean lone = Character.isSurrogate(c)
                            && !(Character.isHighSurrogate(c) && i + 1 < s.length()
                                    && Character.isLowSurrogate(s.charAt(i + 1)))
                            && !(Character.isLowSurrogate(c) && i > 0
                                    && Character.isHighSurrogate(s.charAt(i - 1)));
                    if (c < 0x20 || lone) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
