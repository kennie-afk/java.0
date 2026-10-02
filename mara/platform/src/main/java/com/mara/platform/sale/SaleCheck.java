package com.mara.platform.sale;

import com.mara.platform.money.Money;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * Whether a sale is internally consistent: the arithmetic the terminal claims to have done.
 *
 * <p>A till is believed about <em>what happened at its counter</em>, so a sale that fails
 * these checks is not rejected: the cash is in the drawer either way. It is ingested and
 * <em>flagged</em>, and the difference is carried to a suspense account by the ledger so
 * the books still balance and the discrepancy is visible rather than hidden.
 *
 * <p>The rules are the terminal's own ({@code priceCart} and {@code applyTender}): line
 * net is unit price x quantity, line tax is {@link Money#percentage} of that net, the total
 * is the sum of net and tax, and the applied payments settle the total exactly.
 */
public final class SaleCheck {

    public enum Finding {
        MALFORMED_AMOUNT,
        UNKNOWN_CURRENCY,
        LINE_NET_MISMATCH,
        LINE_TAX_MISMATCH,
        TOTAL_MISMATCH,
        PAYMENTS_DO_NOT_SETTLE_TOTAL,
        NO_LINES,
        NO_PAYMENTS,
        FISCAL_INCONSISTENT,
        VERSION_CASHIER_MISMATCH
    }

    public record Result(List<Finding> findings, long netMinor, long taxMinor, long totalMinor, long appliedMinor) {
        public boolean consistent() {
            return findings.isEmpty();
        }
    }

    private SaleCheck() {
    }

    public static Result check(SaleBody body) {
        List<Finding> findings = new ArrayList<>();
        Currency currency;
        try {
            currency = Currency.getInstance(body.currency());
        } catch (IllegalArgumentException e) {
            return new Result(List.of(Finding.UNKNOWN_CURRENCY), 0, 0, 0, 0);
        }

        long net = 0;
        long tax = 0;
        long applied = 0;
        long total;
        try {
            total = Long.parseLong(body.totalMinor());
            if (body.lines().isEmpty()) {
                findings.add(Finding.NO_LINES);
            }
            for (SaleBody.Line line : body.lines()) {
                long unit = Long.parseLong(line.unitMinor());
                long claimedNet = Long.parseLong(line.netMinor());
                long claimedTax = Long.parseLong(line.taxMinor());
                long expectedNet = Math.multiplyExact(unit, line.qty());
                long expectedTax = Money.of(expectedNet, currency).percentage(line.taxBp()).minor();
                if (line.qty() < 1 || line.taxBp() < 0 || claimedNet != expectedNet) {
                    findings.add(Finding.LINE_NET_MISMATCH);
                }
                if (claimedTax != expectedTax) {
                    findings.add(Finding.LINE_TAX_MISMATCH);
                }
                net = Math.addExact(net, claimedNet);
                tax = Math.addExact(tax, claimedTax);
            }
            if (body.payments().isEmpty()) {
                findings.add(Finding.NO_PAYMENTS);
            }
            for (SaleBody.Payment payment : body.payments()) {
                long amount = Long.parseLong(payment.appliedMinor());
                Long.parseLong(payment.tenderedMinor());
                applied = Math.addExact(applied, amount);
            }
        } catch (NumberFormatException | ArithmeticException e) {
            return new Result(List.of(Finding.MALFORMED_AMOUNT), 0, 0, 0, 0);
        }

        if (total != net + tax) {
            findings.add(Finding.TOTAL_MISMATCH);
        }
        if (applied != total) {
            findings.add(Finding.PAYMENTS_DO_NOT_SETTLE_TOTAL);
        }
        boolean numbered = SaleBody.Fiscal.NUMBERED.equals(body.fiscal().status());
        boolean pending = SaleBody.Fiscal.PENDING.equals(body.fiscal().status());
        if (!(numbered || pending) || numbered == body.fiscal().number().isEmpty()
                || (numbered && !body.fiscal().number().matches("[1-9][0-9]{0,17}"))) {
            findings.add(Finding.FISCAL_INCONSISTENT);
        }
        if (SaleBody.V2.equals(body.version()) != (body.cashier() != null)) {
            findings.add(Finding.VERSION_CASHIER_MISMATCH);
        }
        return new Result(List.copyOf(findings), net, tax, total, applied);
    }
}
