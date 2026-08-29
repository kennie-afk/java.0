package com.kenyarealestate.user.config;

import com.kenyarealestate.user.repository.UserRepository;
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

    @Value("${s3.enabled}")
    private boolean s3Enabled;

    @Value("${services.internal-secret}")
    private String internalSecret;

    private final UserRepository userRepository;
    private final Environment environment;

    public StartupChecks(UserRepository userRepository, Environment environment) {
        this.userRepository = userRepository;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        checkInternalSecret();
        if (!s3Enabled) {
            log.info("############################################################");
            log.info("# USER-SERVICE: storing uploads on local disk (storage.local-dir)");
            log.info("# Durable as long as the container's volume persists, but not");
            log.info("# shared across replicas if this service is scaled horizontally.");
            log.info("# Set S3_ENABLED=true with real credentials for a multi-instance");
            log.info("# or multi-region production deployment.");
            log.info("############################################################");
        }

        if (!userRepository.existsBySuperAdminTrue()) {
            log.warn("############################################################");
            log.warn("# USER-SERVICE: no super admin account exists.");
            log.warn("# V5__super_admin.sql promotes a specific account by email on");
            log.warn("# migration run - if that email doesn't exist in this database,");
            log.warn("# the UPDATE silently matches zero rows and nobody gets promoted.");
            log.warn("# The system currently has no undeletable admin account.");
            log.warn("############################################################");
        }
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
            log.warn("# USER-SERVICE: services.internal-secret {}", reason);
            log.warn("# This is only acceptable for local development. Anything that");
            log.warn("# can reach this secret could forge internal service-to-service");
            log.warn("# requests to user-service.");
            log.warn("############################################################");
        } else {
            log.error("############################################################");
            log.error("# USER-SERVICE: services.internal-secret {}", reason);
            log.error("# Set INTERNAL_SECRET to a unique, randomly generated value of");
            log.error("# at least {} characters before running outside local", MIN_INTERNAL_SECRET_LENGTH);
            log.error("# development - every internal-only endpoint trusts this secret.");
            log.error("############################################################");
        }
    }

    private boolean isLocalOrDevProfile() {
        for (String p : environment.getActiveProfiles()) {
            if ("local".equalsIgnoreCase(p) || "dev".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p)) return true;
        }
        return false;
    }
}
