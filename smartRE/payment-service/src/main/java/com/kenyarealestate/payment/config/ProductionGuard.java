package com.kenyarealestate.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to start a production deployment that would take money through a stand-in.
 *
 * <p>Set {@code SMARTRE_ENVIRONMENT=production} on a real deployment. Mock mode "accepts" any
 * push without money moving, the default shortcode (174379) and the sandbox URLs belong to
 * Safaricom's test environment, and a placeholder consumer key or passkey fails at the provider
 * after the buyer has already been shown a payment screen. Any of these facing real buyers is a
 * payment that cannot complete, or, in mock mode, one that appears to.
 */
@Component
public class ProductionGuard {

    static final String SANDBOX_SHORTCODE = "174379";

    public ProductionGuard(
            @Value("${smartre.environment:development}") String environment,
            @Value("${mpesa.mode:daraja}") String mode,
            @Value("${mpesa.consumer-key:}") String consumerKey,
            @Value("${mpesa.consumer-secret:}") String consumerSecret,
            @Value("${mpesa.passkey:}") String passkey,
            @Value("${mpesa.shortcode:}") String shortcode,
            @Value("${mpesa.callback-url:}") String callbackUrl,
            @Value("${mpesa.auth-url:}") String authUrl,
            @Value("${mpesa.stk-push-url:}") String stkPushUrl) {
        List<String> problems = violations(environment, mode, consumerKey, consumerSecret, passkey, shortcode,
                callbackUrl, authUrl, stkPushUrl);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start payment-service in production: " + String.join("; ", problems));
        }
    }

    static boolean isProduction(String environment) {
        return environment != null && "production".equalsIgnoreCase(environment.trim());
    }

    static boolean isPlaceholder(String value) {
        if (value == null || value.isBlank()) return true;
        String v = value.trim().toLowerCase();
        return v.equals("placeholder") || v.equals("changeme") || v.equals("change-me")
                || v.startsWith("your-") || v.startsWith("replace-me") || v.equals("xxx");
    }

    /** Pure, so it is testable without a Spring context. Empty when the configuration is fit. */
    public static List<String> violations(String environment, String mode, String consumerKey,
                                          String consumerSecret, String passkey, String shortcode,
                                          String callbackUrl, String authUrl, String stkPushUrl) {
        List<String> problems = new ArrayList<>();
        if (!isProduction(environment)) return problems;

        if ("mock".equalsIgnoreCase(mode == null ? "" : mode.trim())) {
            problems.add("MPESA_MODE is 'mock' but production needs 'daraja' (mock accepts any push without money moving)");
        }
        if (isPlaceholder(consumerKey)) problems.add("MPESA_CONSUMER_KEY is unset or a placeholder");
        if (isPlaceholder(consumerSecret)) problems.add("MPESA_CONSUMER_SECRET is unset or a placeholder");
        if (isPlaceholder(passkey)) problems.add("MPESA_PASSKEY is unset or a placeholder");
        if (shortcode == null || shortcode.isBlank() || SANDBOX_SHORTCODE.equals(shortcode.trim())) {
            problems.add("MPESA_SHORTCODE is unset or still Safaricom's sandbox shortcode " + SANDBOX_SHORTCODE);
        }
        if (callbackUrl == null || callbackUrl.isBlank() || callbackUrl.contains("your-domain.com")) {
            problems.add("MPESA_CALLBACK_URL is unset or still the placeholder, so no payment could ever be confirmed");
        }
        if (containsSandbox(authUrl) || containsSandbox(stkPushUrl)) {
            problems.add("MPESA_AUTH_URL / MPESA_STK_PUSH_URL still point at sandbox.safaricom.co.ke");
        }
        return problems;
    }

    private static boolean containsSandbox(String url) {
        return url != null && url.toLowerCase().contains("sandbox.");
    }
}
