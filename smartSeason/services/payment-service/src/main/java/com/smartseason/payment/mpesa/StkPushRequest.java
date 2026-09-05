package com.smartseason.payment.mpesa;

import java.math.BigDecimal;

public record StkPushRequest(
        String phoneNumber,
        BigDecimal amount,
        String accountReference,
        String description,
        String callbackUrl) {

    public StkPushRequest {
        if (phoneNumber == null || !phoneNumber.matches("254[17]\\d{8}")) {
            throw new IllegalArgumentException(
                    "M-Pesa requires a msisdn in 2547XXXXXXXX form but got: " + phoneNumber);
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "M-Pesa accepts whole shillings only but got: " + amount.toPlainString());
        }
    }
}
