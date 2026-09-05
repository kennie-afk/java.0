package com.smartseason.payment.mpesa;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartseason.mpesa")
public record MpesaProperties(
        String mode,
        String baseUrl,
        String consumerKey,
        String consumerSecret,
        String shortCode,
        String passkey,
        String initiatorName,
        String securityCredential,
        String callbackSecret) {

    public MpesaProperties {
        if (mode == null || mode.isBlank()) {
            mode = "mock";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://sandbox.safaricom.co.ke";
        }
    }
}
