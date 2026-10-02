package com.hms.billing;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MpesaConfig {

    /** hms.mpesa.mode = mock (default) or live. Live is intentionally not implemented. */
    @Bean
    MpesaGateway mpesaGateway(@Value("${hms.mpesa.mode:mock}") String mode) {
        if ("live".equalsIgnoreCase(mode)) {
            return new MpesaGateway() {
                @Override
                public StkRequest initiate(String phone, BigDecimal amount, String reference, String description) {
                    throw new com.hms.platform.web.ApiException(org.springframework.http.HttpStatus.NOT_IMPLEMENTED, "mpesa_not_configured",
                            "Live M-Pesa is not implemented in this build. It needs Daraja credentials and a verified integration; run with HMS_MPESA_MODE=mock to simulate.");
                }

                @Override
                public boolean isMock() {
                    return false;
                }
            };
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
