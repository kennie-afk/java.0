package com.mara.platform.sale;

import java.util.List;
import java.util.Objects;

/**
 * The sale as the terminal commits to it, field for field the terminal's {@code SaleBody}
 * (apps/terminal/src/lib/sale.ts). Amounts are decimal strings of minor units, exactly as
 * the terminal serialises them, so a value beyond JavaScript's safe integer range still
 * survives the trip.
 *
 * <p>This type carries no behaviour that could disagree with the terminal: the canonical
 * encoding and the checks live in {@link SaleCanonical} and {@link SaleCheck}.
 */
public record SaleBody(
        String version,
        String currency,
        List<Line> lines,
        List<Payment> payments,
        String totalMinor,
        Fiscal fiscal,
        Cashier cashier) {

    public static final String V1 = "mara.sale.v1";
    public static final String V2 = "mara.sale.v2";

    public record Line(
            String sku, String name, long qty, String unitMinor, long taxBp, String netMinor, String taxMinor) {
    }

    public record Payment(String method, String appliedMinor, String reference, String tenderedMinor) {
    }

    public record Fiscal(String status, String number) {
        public static final String NUMBERED = "NUMBERED";
        public static final String PENDING = "FISCAL_PENDING";
    }

    public record Cashier(String staffId, String staffNumber, String name) {
    }

    public SaleBody {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(payments, "payments");
        Objects.requireNonNull(totalMinor, "totalMinor");
        Objects.requireNonNull(fiscal, "fiscal");
        lines = List.copyOf(lines);
        payments = List.copyOf(payments);
    }
}
