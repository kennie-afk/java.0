package com.soko.mpesa;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code soko.mpesa.*}. Every field is optional in {@code mock}
 * mode (the default) -- only {@code live} mode actually calls Safaricom, and
 * only then do the Daraja credentials need to be real. See
 * {@link MockMpesaGateway} and {@link DarajaMpesaGateway}.
 */
@ConfigurationProperties(prefix = "soko.mpesa")
public record MpesaProperties(
        String mode,
        String baseUrl,
        String consumerKey,
        String consumerSecret,
        String shortCode,
        String passkey,
        String callbackSecret,
        String callbackUrl) {

    public MpesaProperties {
        if (mode == null || mode.isBlank()) {
            mode = "mock";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://sandbox.safaricom.co.ke";
        }
        if (callbackUrl == null || callbackUrl.isBlank()) {
            callbackUrl = "http://localhost:8090/v1/public/mpesa/stk-callback";
        }
    }
}
