package com.smartseason.payout.disburse;

import java.math.BigDecimal;
import java.util.UUID;

public record PayeeSnapshot(
        UUID payeeId,
        String payeeName,
        String payeePhone,
        BigDecimal amount,
        boolean kycVerified,
        boolean hasOpenFraudCase,
        boolean underDispute,
        boolean sanctioned) {
}
