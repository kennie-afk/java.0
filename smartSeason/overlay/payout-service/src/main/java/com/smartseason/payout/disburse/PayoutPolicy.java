package com.smartseason.payout.disburse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PayoutPolicy {

    private final BigDecimal kycRequiredAbove;
    private final BigDecimal maximumSinglePayout;

    public PayoutPolicy(BigDecimal kycRequiredAbove, BigDecimal maximumSinglePayout) {
        this.kycRequiredAbove = kycRequiredAbove;
        this.maximumSinglePayout = maximumSinglePayout;
    }

    public static PayoutPolicy defaults() {
        return new PayoutPolicy(new BigDecimal("70000"), new BigDecimal("500000"));
    }

    public PayoutEligibility assess(PayeeSnapshot payee) {
        List<String> reasons = new ArrayList<>();

        if (payee.amount() == null || payee.amount().signum() <= 0) {
            reasons.add("payout amount must be positive");
        }
        if (payee.sanctioned()) {
            reasons.add("payee appears on a sanctions list");
        }
        if (payee.hasOpenFraudCase()) {
            reasons.add("an open fraud case is pending review");
        }
        if (payee.underDispute()) {
            reasons.add("the underlying transaction is under dispute");
        }
        if (payee.payeePhone() == null || payee.payeePhone().isBlank()) {
            reasons.add("no payout destination on file");
        }
        if (payee.amount() != null
                && payee.amount().compareTo(kycRequiredAbove) > 0
                && !payee.kycVerified()) {
            reasons.add("KYC is required for payouts above %s"
                    .formatted(kycRequiredAbove.toPlainString()));
        }
        if (payee.amount() != null && payee.amount().compareTo(maximumSinglePayout) > 0) {
            reasons.add("amount exceeds the %s single-payout ceiling"
                    .formatted(maximumSinglePayout.toPlainString()));
        }

        return reasons.isEmpty() ? PayoutEligibility.allow() : PayoutEligibility.deny(reasons);
    }
}
