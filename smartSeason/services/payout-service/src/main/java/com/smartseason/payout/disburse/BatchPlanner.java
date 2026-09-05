package com.smartseason.payout.disburse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BatchPlanner {

    private final PayoutPolicy policy;

    public BatchPlanner(PayoutPolicy policy) {
        this.policy = policy;
    }

    public BatchPlan plan(List<PayeeSnapshot> payees) {
        List<PayeeSnapshot> payable = new ArrayList<>();
        List<BatchPlan.Blocked> blocked = new ArrayList<>();
        BigDecimal payableTotal = BigDecimal.ZERO;
        BigDecimal blockedTotal = BigDecimal.ZERO;

        for (PayeeSnapshot payee : payees) {
            PayoutEligibility eligibility = policy.assess(payee);
            BigDecimal amount = payee.amount() == null ? BigDecimal.ZERO : payee.amount();

            if (eligibility.payable()) {
                payable.add(payee);
                payableTotal = payableTotal.add(amount);
            } else {
                blocked.add(new BatchPlan.Blocked(payee, eligibility));
                blockedTotal = blockedTotal.add(amount);
            }
        }

        return new BatchPlan(List.copyOf(payable), List.copyOf(blocked), payableTotal, blockedTotal);
    }
}
