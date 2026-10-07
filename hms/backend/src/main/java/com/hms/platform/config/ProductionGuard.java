package com.hms.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a production deployment that would pretend to take money. The mock M-Pesa gateway
 * "completes" payments that nobody paid, so in production it is allowed only when someone has said so
 * on purpose (for example a pilot that records cash and card only and never offers M-Pesa to a customer).
 */
@Component
public class ProductionGuard {

    public ProductionGuard(@Value("${hms.environment:development}") String environment,
                           @Value("${hms.mpesa.mode:mock}") String mpesaMode,
                           @Value("${hms.mpesa.allow-mock-in-production:false}") boolean allowMock) {
        verify(environment, mpesaMode, allowMock);
    }

    public static void verify(String environment, String mpesaMode, boolean allowMockMpesa) {
        boolean production = "production".equalsIgnoreCase(environment) || "prod".equalsIgnoreCase(environment);
        boolean mock = !"daraja".equalsIgnoreCase(mpesaMode) && !"live".equalsIgnoreCase(mpesaMode);
        if (production && mock && !allowMockMpesa) {
            throw new IllegalStateException("HMS_ENVIRONMENT=production with the mock M-Pesa gateway: simulated payments would be recorded as real. "
                    + "Set HMS_MPESA_MODE=daraja with credentials, or set HMS_MPESA_ALLOW_MOCK_IN_PRODUCTION=true if M-Pesa is deliberately not offered.");
        }
    }
}
