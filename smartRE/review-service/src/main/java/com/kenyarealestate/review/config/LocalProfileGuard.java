package com.kenyarealestate.review.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * application-local.yaml turns on {@code ddl-auto: update} and carries a default database
 * password, both of which are for a developer's own machine: Hibernate would reshape a real
 * schema to match the entities, outside Flyway, with no review.
 *
 * <p>Nothing stops {@code SPRING_PROFILES_ACTIVE=local} from reaching a deployment by copy and
 * paste, so this refuses to start when the local profile is active and
 * {@code SMARTRE_ENVIRONMENT=production}. Production profiles use {@code ddl-auto: validate} and
 * let Flyway own the schema.
 */
@Component
@Profile("local")
public class LocalProfileGuard {

    public LocalProfileGuard(@Value("${SMARTRE_ENVIRONMENT:development}") String environment) {
        if (isProduction(environment)) {
            throw new IllegalStateException(
                    "Refusing to start review-service: the 'local' profile (ddl-auto: update, default database "
                            + "password) is active while SMARTRE_ENVIRONMENT=production");
        }
    }

    static boolean isProduction(String environment) {
        return environment != null && "production".equalsIgnoreCase(environment.trim());
    }
}
