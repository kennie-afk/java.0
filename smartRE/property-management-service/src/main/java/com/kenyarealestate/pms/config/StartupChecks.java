package com.kenyarealestate.pms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class StartupChecks implements CommandLineRunner {

    private static final String KNOWN_DEFAULT_INTERNAL_SECRET = "smartre-internal-secret-2026";
    private static final int MIN_INTERNAL_SECRET_LENGTH = 20;

    @Value("${services.internal-secret}")
    private String internalSecret;

    private final Environment environment;

    public StartupChecks(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        checkInternalSecret();
    }

    private void checkInternalSecret() {
        boolean isKnownDefault = KNOWN_DEFAULT_INTERNAL_SECRET.equals(internalSecret);
        boolean tooShort = !StringUtils.hasText(internalSecret) || internalSecret.length() < MIN_INTERNAL_SECRET_LENGTH;
        if (!isKnownDefault && !tooShort) return;

        String reason = isKnownDefault
                ? "matches the known committed default value"
                : "is shorter than the recommended minimum of " + MIN_INTERNAL_SECRET_LENGTH + " characters";

        if (isLocalOrDevProfile()) {
            log.warn("############################################################");
            log.warn("# PROPERTY-MANAGEMENT-SERVICE: services.internal-secret {}", reason);
            log.warn("# This is only acceptable for local development. Anything that");
            log.warn("# can reach this secret could forge internal service-to-service");
            log.warn("# requests to property-management-service.");
            log.warn("############################################################");
        } else {
            throw new IllegalStateException(
                    "PROPERTY-MANAGEMENT-SERVICE: services.internal-secret " + reason
                            + "; set INTERNAL_SECRET to a unique, randomly generated value of at least "
                            + MIN_INTERNAL_SECRET_LENGTH
                            + " characters before running outside local development, because every "
                            + "internal-only endpoint trusts this secret");
        }
    }

    private boolean isLocalOrDevProfile() {
        for (String p : environment.getActiveProfiles()) {
            if ("local".equalsIgnoreCase(p) || "dev".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p)) return true;
        }
        return false;
    }
}
