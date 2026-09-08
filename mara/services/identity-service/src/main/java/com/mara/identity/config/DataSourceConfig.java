package com.mara.identity.config;

import com.mara.identity.tenant.TenantAwareDataSource;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two data sources on purpose, connecting as two different database roles.
 *
 * <p>Flyway runs as the owner, which may create and alter tables. The application runs
 * as {@code mara_app}, which may not — and critically, cannot bypass row-level
 * security. A table owner bypasses RLS by default, so an application connecting as the
 * owner would make every policy in {@code V2__row_level_security.sql} decorative.
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
    public DataSource ownerDataSource(DataSourceProperties ownerDataSourceProperties) {
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
     */
    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties appDataSourceProperties) {
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
