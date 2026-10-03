package com.smartseason.payment.platform;

import com.smartseason.payment.mpesa.MpesaProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MpesaProperties.class)
public class MpesaConfig {

    /**
     * Set SMARTSEASON_ENVIRONMENT=production on a real deployment. Mock M-Pesa accepts any callback and
     * "pays" without money moving, so the process refuses to start with it rather than quietly
     * letting goods or services be released against payments that never happened.
     */
    public MpesaConfig(MpesaProperties properties,
                       @Value("${smartseason.environment:development}") String environment) {
        String problem = productionProblem(environment, properties.mode());
        if (problem != null) {
            throw new IllegalStateException("Refusing to start in production: " + problem);
        }
    }

    /** Pure so it can be tested without a Spring context; null when the configuration is fit. */
    static String productionProblem(String environment, String mpesaMode) {
        if (!"production".equalsIgnoreCase(environment == null ? "" : environment.trim())) {
            return null;
        }
        return "live".equalsIgnoreCase(mpesaMode)
                ? null
                : "SMARTSEASON_MPESA_MODE is '" + mpesaMode + "' but production needs 'live' (mock accepts any callback)";
    }
}
