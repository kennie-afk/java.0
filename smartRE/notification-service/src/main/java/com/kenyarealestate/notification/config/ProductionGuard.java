package com.kenyarealestate.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to start a production deployment that would quietly do nothing where the platform
 * promises something.
 *
 * <p>Set {@code SMARTRE_ENVIRONMENT=production} on a real deployment. Outside production
 * everything keeps its supported "unconfigured" mode (log instead of send, manual review instead
 * of a provider call), because that is what makes local development and demos work. In
 * production the same silence is a defect: a seller waiting for a code that is only ever logged,
 * or a title that no registry was asked about, looks to the user exactly like a working system.
 *
 * <p>A provider that is genuinely not wanted yet can be acknowledged with
 * {@code SMARTRE_ALLOW_UNCONFIGURED_PROVIDERS=true}. The process then starts and says so loudly
 * on every start; the point is that it is a decision someone made, not a default nobody noticed.
 * A provider that IS switched on with a placeholder key is never acceptable.
 */
@Slf4j
@Component
public class ProductionGuard {

    public ProductionGuard(
            @Value("${smartre.environment:development}") String environment,
            @Value("${smartre.allow-unconfigured-providers:false}") boolean allowUnconfigured,
            @Value("${sms.username:}") String smsUsername,
            @Value("${sms.api-key:}") String smsApiKey,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword) {
        List<String> problems = violations(environment, allowUnconfigured, smsUsername, smsApiKey,
                mailHost, mailUsername, mailPassword);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start notification-service in production: " + String.join("; ", problems));
        }
        if (isProduction(environment) && allowUnconfigured
                && (isBlank(smsApiKey) || isBlank(smsUsername) || isBlank(mailHost))) {
            log.warn("SMARTRE_ALLOW_UNCONFIGURED_PROVIDERS=true: SMS and/or email will be LOGGED, not delivered");
        }
    }

    static boolean isProduction(String environment) {
        return environment != null && "production".equalsIgnoreCase(environment.trim());
    }

    /** Blank, or one of the stand-ins the example configuration and the k8s manifests ship with. */
    static boolean isPlaceholder(String value) {
        if (value == null || value.isBlank()) return true;
        String v = value.trim().toLowerCase();
        return v.equals("placeholder") || v.equals("changeme") || v.equals("change-me")
                || v.startsWith("your-") || v.startsWith("replace-me") || v.equals("xxx");
    }

    /** Pure, so it is testable without a Spring context. Empty when the configuration is fit. */
    public static List<String> violations(String environment, boolean allowUnconfigured, String smsUsername,
                                          String smsApiKey, String mailHost, String mailUsername,
                                          String mailPassword) {
        List<String> problems = new ArrayList<>();
        if (!isProduction(environment)) return problems;

        // A key that is set but is a stand-in is worse than none: the channel believes it is
        // configured, posts to the provider, and every message fails there.
        boolean userBlank = isBlank(smsUsername);
        boolean keyBlank = isBlank(smsApiKey);
        boolean standIn = (!userBlank && isPlaceholder(smsUsername)) || (!keyBlank && isPlaceholder(smsApiKey));
        if (standIn || userBlank != keyBlank) {
            problems.add("SMS_USERNAME/SMS_API_KEY are partly set or still placeholders; set both to real "
                    + "provider credentials, or leave both empty and acknowledge it with "
                    + "SMARTRE_ALLOW_UNCONFIGURED_PROVIDERS=true");
        } else if (userBlank && !allowUnconfigured) {
            problems.add("SMS_USERNAME/SMS_API_KEY are not set, so every SMS would only be logged "
                    + "(set them, or acknowledge with SMARTRE_ALLOW_UNCONFIGURED_PROVIDERS=true)");
        }

        if (isBlank(mailHost)) {
            if (!allowUnconfigured) {
                problems.add("MAIL_HOST is not set, so every email would only be logged "
                        + "(set it, or acknowledge with SMARTRE_ALLOW_UNCONFIGURED_PROVIDERS=true)");
            }
        } else if (isPlaceholder(mailHost) || isPlaceholder(mailUsername) || isPlaceholder(mailPassword)) {
            problems.add("MAIL_HOST is set but it, MAIL_USERNAME or MAIL_PASSWORD is empty or a placeholder");
        }
        return problems;
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
