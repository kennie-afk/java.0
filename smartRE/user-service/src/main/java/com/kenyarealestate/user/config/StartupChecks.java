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
            // Warn, not info: this is a constraint on how the service may be deployed,
            // and it was previously easy to miss in a busy startup log.
            log.warn("############################################################");
            log.warn("# USER-SERVICE: storing uploads on local disk (storage.local-dir)");
            log.warn("# This instance is the only one that can read what it writes.");
            log.warn("# Running more than one replica against a volume that is not");
            log.warn("# shared will lose documents — an upload handled by one pod is");
            log.warn("# simply missing from the other, and shows up as an intermittent");
            log.warn("# 404 rather than as an error anyone can trace.");
            log.warn("# k8s/user-service.yaml pins this deployment to one replica for");
            log.warn("# exactly this reason. Set S3_ENABLED=true with real credentials");
            log.warn("# before scaling it.");
            log.warn("############################################################");
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
            throw new IllegalStateException(
                    "USER-SERVICE: services.internal-secret " + reason
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
