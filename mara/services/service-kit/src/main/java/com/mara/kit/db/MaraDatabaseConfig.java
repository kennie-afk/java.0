package com.mara.kit.db;

import com.mara.kit.tenant.TenantAwareDataSource;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The two-role database wiring, the same shape identity-service proved: Flyway runs as the
 * owner, every request runs as {@code mara_app}, which cannot alter the schema and cannot
 * bypass row-level security. The pieces that were found broken the hard way there are
 * carried over deliberately: the {@code @Qualifier} on the owner properties (an
 * unqualified parameter resolves to the {@code @Primary} app properties and Flyway silently
 * connects as the wrong role), the login grant that gives {@code mara_app} its password on
 * every start (migrations create it NOLOGIN so no credential lives in a migration), and the
 * tenant binding at the instant a transaction begins.
 *
 * <p>Properties: {@code mara.datasource.owner.*} and {@code mara.datasource.app.*}, with
 * {@code mara.datasource.app.password} read for the login grant. Import this class from the
 * service's application class.
 */
@Configuration
public class MaraDatabaseConfig {

    @Bean
    @ConfigurationProperties("mara.datasource.owner")
    public DataSourceProperties ownerDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "ownerDataSource")
    public DataSource ownerDataSource(
            @Qualifier("ownerDataSourceProperties") DataSourceProperties props) {
        DatabaseBootstrap.ensureExists(props.getUrl(), props.getUsername(), props.getPassword());
        return props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean(initMethod = "migrate")
    public Flyway flyway(@Qualifier("ownerDataSource") DataSource ownerDataSource) {
        return Flyway.configure()
                .dataSource(ownerDataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }

    @Bean(initMethod = "run")
    @DependsOn("flyway")
    public Runnable appRoleLoginBootstrap(
            @Qualifier("ownerDataSource") DataSource ownerDataSource,
            @Value("${mara.datasource.app.password}") String appPassword) {
        JdbcTemplate ownerJdbc = new JdbcTemplate(ownerDataSource);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(ownerDataSource));
        return () -> transaction.executeWithoutResult(status -> {
            ownerJdbc.queryForObject(
                    "SELECT set_config('mara.bootstrap_password', ?, true)", String.class, appPassword);
            ownerJdbc.execute(
                    "DO $$ BEGIN "
                            + "EXECUTE format('ALTER ROLE mara_app WITH LOGIN PASSWORD %L', "
                            + "current_setting('mara.bootstrap_password')); "
                            + "END $$;");
        });
    }

    @Bean
    @Primary
    @ConfigurationProperties("mara.datasource.app")
    public DataSourceProperties appDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @DependsOn("appRoleLoginBootstrap")
    public DataSource dataSource(@Qualifier("appDataSourceProperties") DataSourceProperties props) {
        HikariDataSource pool = props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        return new TenantAwareDataSource(pool);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
