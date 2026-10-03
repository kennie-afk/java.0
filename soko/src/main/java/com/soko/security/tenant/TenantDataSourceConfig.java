package com.soko.security.tenant;

import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wraps the application's DataSource so every transaction carries its tenant. A post-processor
 * rather than a replacement bean, so Spring Boot's own pool configuration is untouched.
 */
@Configuration
public class TenantDataSourceConfig {

    /** Flyway substitutes placeholders as text, so a value with a quote would break (or inject into) the SQL. */
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9_.+=@#%^*!~-]{16,128}$");

    @Bean
    static BeanPostProcessor tenantAwareDataSource(
            @Value("${soko.rls.system-key:}") String systemKey,
            @Value("${soko.rls.app-password:}") String appPassword) {
        require("SOKO_SYSTEM_KEY", systemKey);
        require("SOKO_DB_APP_PASSWORD", appPassword);
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof DataSource ds && !(bean instanceof TenantAwareDataSource)
                        ? new TenantAwareDataSource(ds, systemKey)
                        : bean;
            }
        };
    }

    private static void require(String name, String value) {
        if (!SAFE.matcher(value).matches()) {
            throw new IllegalStateException(name + " must be 16-128 characters from A-Z a-z 0-9 _ . + = @ # % ^ * ! ~ -"
                    + " (no quotes or spaces: it is substituted into a migration)");
        }
    }
}
