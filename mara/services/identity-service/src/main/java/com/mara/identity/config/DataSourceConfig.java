package com.mara.identity.config;

import com.mara.identity.tenant.TenantAwareDataSource;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two data sources on purpose, connecting as two different database roles.
 *
 * <p>Flyway runs as the owner, which may create and alter tables. The application runs
 * as {@code mara_app}, which may not — and critically, cannot bypass row-level
 * security. A table owner bypasses RLS by default, so an application connecting as the
 * owner would make every policy in {@code V2__row_level_security.sql} decorative.
 *
 * <p>The {@code @Qualifier} on each {@code DataSourceProperties} parameter below is
 * load-bearing, not decorative. Both {@code ownerDataSourceProperties} and
 * {@code appDataSourceProperties} return the same type with no other distinguishing
 * annotation, and {@code appDataSourceProperties} is {@code @Primary} — without an
 * explicit qualifier, {@code ownerDataSource}'s parameter resolved to the
 * {@code @Primary} bean instead of the one its own parameter name suggested, so the
 * "owner" connection silently authenticated as {@code mara_app}. Nothing catches this at
 * compile time and Flyway's own error for it — "password authentication failed for user
 * mara_app" while running as what is supposed to be the owner — does not obviously point
 * at dependency injection. It only ever surfaced running the real Spring context against
 * a real, freshly-created database; every prior test built {@code EnrolmentRepository}
 * and friends by hand, bypassing this class entirely.
 */
@Configuration
public class DataSourceConfig {

    /** Owns the schema. Used by Flyway only, never by request handling. */
    @Bean
    @ConfigurationProperties("mara.datasource.owner")
    public DataSourceProperties ownerDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "ownerDataSource")
    public DataSource ownerDataSource(
            @Qualifier("ownerDataSourceProperties") DataSourceProperties ownerDataSourceProperties) {
        return ownerDataSourceProperties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Primary
    @ConfigurationProperties("mara.datasource.app")
    public DataSourceProperties appDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * What the application uses. Wrapped so every connection carries the request's
     * tenant into the session variable the RLS policies read.
     *
     * <p>Depends on {@code appRoleLoginBootstrap}, not just {@code flyway}: Spring does
     * not otherwise order these two {@code @Configuration} classes' beans against each
     * other, and this pool authenticating as {@code mara_app} eagerly at construction
     * time would fail on a fresh database if it built before the role could log in —
     * migrations alone leave {@code mara_app} as {@code NOLOGIN}. See
     * {@link AppRoleLoginConfig}.
     */
    @Bean
    @Primary
    @DependsOn("appRoleLoginBootstrap")
    public DataSource dataSource(
            @Qualifier("appDataSourceProperties") DataSourceProperties appDataSourceProperties) {
        HikariDataSource pool = appDataSourceProperties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        return new TenantAwareDataSource(pool);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
