package com.mara.identity.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Migrations run as the schema owner, never as the application role.
 *
 * <p>The application role deliberately cannot create or alter tables, and cannot bypass
 * row-level security. If Flyway shared its connection, either the migrations would fail
 * or — worse — the application would have been granted rights that make every RLS policy
 * in V2 advisory.
 */
@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway flyway(@Qualifier("ownerDataSource") DataSource ownerDataSource) {
        return Flyway.configure()
                .dataSource(ownerDataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }
}
