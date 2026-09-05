package com.smartseason.payout.disburse;

import java.math.BigDecimal;
import java.util.List;

public record BatchPlan(
        List<PayeeSnapshot> payable,
        List<Blocked> blocked,
        BigDecimal payableTotal,
        BigDecimal blockedTotal) {

    public record Blocked(PayeeSnapshot payee, PayoutEligibility eligibility) {
    }

    public int payableCount() {
        return payable.size();
    }

    public int blockedCount() {
        return blocked.size();
    }
}
