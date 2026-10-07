package com.soko.platform;

import com.soko.mpesa.MpesaProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a production deployment that is still wired for a demo.
 *
 * <p>Set {@code SOKO_ENVIRONMENT=production} on a real deployment. Mock M-Pesa accepts any
 * callback and "pays" without money moving, a short JWT secret can be brute-forced, and the mock
 * customer that answers PIN prompts by itself exists only to make a demo run. Any of these facing
 * real customers is a way to get goods without paying, so the process stops with every problem
 * listed, instead of starting and failing quietly later.
 */
@Component
public class ProductionGuard {

    public ProductionGuard(
            MpesaProperties mpesa,
            @Value("${soko.environment:development}") String environment,
            @Value("${soko.jwt.secret}") String jwtSecret,
            @Value("${SOKO_MPESA_MOCK_AUTOCOMPLETE_SECONDS:0}") int mockAutocompleteSeconds,
            @Value("${soko.mail.enabled:false}") boolean mailEnabled) {
        List<String> problems =
                violations(environment, mpesa.mode(), jwtSecret, mockAutocompleteSeconds, mailEnabled);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start in production: " + String.join("; ", problems));
        }
    }

    /** Pure so it can be tested without a Spring context. Empty when the configuration is fit. */
    public static List<String> violations(
            String environment, String mpesaMode, String jwtSecret, int mockAutocompleteSeconds,
            boolean mailEnabled) {
        List<String> problems = violations(environment, mpesaMode, jwtSecret, mockAutocompleteSeconds);
        if ("production".equalsIgnoreCase(environment == null ? "" : environment.trim()) && !mailEnabled) {
            problems.add("SOKO_MAIL_ENABLED is not true: password reset links would only be logged, never sent");
        }
        return problems;
    }

    public static List<String> violations(
            String environment, String mpesaMode, String jwtSecret, int mockAutocompleteSeconds) {
        List<String> problems = new ArrayList<>();
        if (!"production".equalsIgnoreCase(environment == null ? "" : environment.trim())) {
            return problems;
        }
        if (!"live".equalsIgnoreCase(mpesaMode)) {
            problems.add("SOKO_MPESA_MODE is '" + mpesaMode + "' but production needs 'live' (mock accepts any callback)");
        }
        if (jwtSecret == null || jwtSecret.length() < 32) {
            problems.add("the JWT secret must be at least 32 characters");
        }
        if (mockAutocompleteSeconds > 0) {
            problems.add("SOKO_MPESA_MOCK_AUTOCOMPLETE_SECONDS must be 0 in production");
        }
        return problems;
    }
}
