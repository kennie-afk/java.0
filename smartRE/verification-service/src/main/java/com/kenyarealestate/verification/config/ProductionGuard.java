package com.kenyarealestate.verification.config;

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
            @Value("${services.smile-identity-enabled:false}") boolean smileEnabled,
            @Value("${services.smile-identity-partner-id:}") String smilePartnerId,
            @Value("${services.smile-identity-api-key:}") String smileApiKey,
            @Value("${services.ardhisasa-enabled:false}") boolean ardhisasaEnabled,
            @Value("${services.ardhisasa-api-key:}") String ardhisasaApiKey,
            @Value("${services.document-analysis-enabled:false}") boolean analysisEnabled,
            @Value("${services.gemini-api-key:}") String geminiApiKey) {
        List<String> problems = violations(environment, allowUnconfigured, smileEnabled, smilePartnerId,
                smileApiKey, ardhisasaEnabled, ardhisasaApiKey, analysisEnabled, geminiApiKey);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start verification-service in production: " + String.join("; ", problems));
        }
        if (isProduction(environment) && allowUnconfigured
                && (!smileEnabled || !ardhisasaEnabled || !analysisEnabled)) {
            log.warn("SMARTRE_ALLOW_UNCONFIGURED_PROVIDERS=true: identity (Smile), registry (Ardhisasa) and/or "
                    + "document analysis are OFF, so those checks fall to manual review");
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
    public static List<String> violations(String environment, boolean allowUnconfigured,
                                          boolean smileEnabled, String smilePartnerId, String smileApiKey,
                                          boolean ardhisasaEnabled, String ardhisasaApiKey,
                                          boolean analysisEnabled, String geminiApiKey) {
        List<String> problems = new ArrayList<>();
        if (!isProduction(environment)) return problems;

        // Switched on with a stand-in key: calls would go out and fail, and the failure would be
        // read as "the seller did not pass" rather than "we are not configured".
        if (smileEnabled && (isPlaceholder(smilePartnerId) || isPlaceholder(smileApiKey))) {
            problems.add("SMILE_IDENTITY_ENABLED=true but SMILE_IDENTITY_PARTNER_ID/SMILE_IDENTITY_API_KEY are unset or placeholders");
        }
        if (ardhisasaEnabled && isPlaceholder(ardhisasaApiKey)) {
            problems.add("ARDHISASA_ENABLED=true but ARDHISASA_API_KEY is unset or a placeholder");
        }
        if (analysisEnabled && isPlaceholder(geminiApiKey)) {
            problems.add("DOCUMENT_ANALYSIS_ENABLED=true but GEMINI_API_KEY is unset or a placeholder");
        }
        if (!allowUnconfigured) {
            if (!smileEnabled) {
                problems.add("Smile Identity is off (SMILE_IDENTITY_ENABLED is not true), so identity checks never reach a provider");
            }
            if (!ardhisasaEnabled) {
                problems.add("Ardhisasa is off (ARDHISASA_ENABLED is not true), so no title is ever checked against the registry");
            }
            if (!analysisEnabled) {
                problems.add("document analysis is off (DOCUMENT_ANALYSIS_ENABLED is not true)");
            }
            if (!problems.isEmpty()) {
                problems.add("enable them with real keys, or acknowledge manual-only operation with "
                        + "SMARTRE_ALLOW_UNCONFIGURED_PROVIDERS=true");
            }
        }
        return problems;
    }
}
