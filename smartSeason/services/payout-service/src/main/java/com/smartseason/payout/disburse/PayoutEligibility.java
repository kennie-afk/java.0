package com.smartseason.payout.disburse;

import java.util.List;

public record PayoutEligibility(
        boolean payable,
        List<String> blockingReasons) {

    public static PayoutEligibility allow() {
        return new PayoutEligibility(true, List.of());
    }

    public static PayoutEligibility deny(List<String> reasons) {
        return new PayoutEligibility(false, List.copyOf(reasons));
    }

    public String summary() {
        return blockingReasons.isEmpty() ? null : String.join("; ", blockingReasons);
    }
}
