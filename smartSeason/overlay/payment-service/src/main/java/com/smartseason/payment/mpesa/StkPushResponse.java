package com.smartseason.payment.mpesa;

public record StkPushResponse(
        boolean accepted,
        String merchantRequestId,
        String checkoutRequestId,
        String responseCode,
        String responseDescription,
        String customerMessage) {
}
