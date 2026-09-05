package com.smartseason.payment.mpesa;

import java.math.BigDecimal;

public record B2cRequest(
        String phoneNumber,
        BigDecimal amount,
        String remarks,
        String occasion,
        String resultUrl,
        String timeoutUrl) {

    public B2cRequest {
        if (phoneNumber == null || !phoneNumber.matches("254[17]\\d{8}")) {
            throw new IllegalArgumentException(
                    "M-Pesa requires a msisdn in 2547XXXXXXXX form but got: " + phoneNumber);
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
