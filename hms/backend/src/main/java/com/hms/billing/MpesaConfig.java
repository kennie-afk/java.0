package com.hms.billing;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MpesaConfig {

    static final int MIN_SECRET = 24;

    /**
     * hms.mpesa.mode = mock (default) or daraja. Daraja is written from the public documentation and has never been run against
     * Safaricom (see {@link DarajaMpesaGateway}). "live" is accepted as an older name for daraja.
     */
    @Bean
    MpesaGateway mpesaGateway(@Value("${hms.mpesa.mode:mock}") String mode,
                              @Value("${hms.mpesa.base-url:https://sandbox.safaricom.co.ke}") String baseUrl,
                              @Value("${hms.mpesa.consumer-key:}") String key,
                              @Value("${hms.mpesa.consumer-secret:}") String secret,
                              @Value("${hms.mpesa.shortcode:}") String shortcode,
                              @Value("${hms.mpesa.passkey:}") String passkey,
                              @Value("${hms.mpesa.callback-base-url:}") String callbackBase,
                              @Value("${hms.mpesa.callback-secret:}") String callbackSecret) {
        if ("daraja".equalsIgnoreCase(mode) || "live".equalsIgnoreCase(mode)) {
            for (String[] required : new String[][] {{"HMS_MPESA_CONSUMER_KEY", key}, {"HMS_MPESA_CONSUMER_SECRET", secret}, {"HMS_MPESA_SHORTCODE", shortcode},
                    {"HMS_MPESA_PASSKEY", passkey}, {"HMS_MPESA_CALLBACK_BASE_URL", callbackBase}}) {
                if (required[1] == null || required[1].isBlank()) {
                    throw new IllegalStateException("HMS_MPESA_MODE=daraja needs " + required[0]);
                }
            }
            if (callbackSecret == null || callbackSecret.length() < MIN_SECRET) {
                throw new IllegalStateException("HMS_MPESA_MODE=daraja needs HMS_MPESA_CALLBACK_SECRET of at least " + MIN_SECRET + " characters");
            }
            String base = callbackBase.endsWith("/") ? callbackBase.substring(0, callbackBase.length() - 1) : callbackBase;
            return new DarajaMpesaGateway(new DarajaMpesaGateway.Config(baseUrl, key, secret, shortcode, passkey,
                    base + "/v1/billing/mpesa/" + callbackSecret + "/confirmation"));
        }
        return new MpesaGateway() {
            @Override
            public StkRequest initiate(String phone, BigDecimal amount, String reference, String description) {
                return new StkRequest("MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(), "simulated; no prompt was sent to " + phone);
            }

            @Override
            public boolean isMock() {
                return true;
            }
        };
    }
}
