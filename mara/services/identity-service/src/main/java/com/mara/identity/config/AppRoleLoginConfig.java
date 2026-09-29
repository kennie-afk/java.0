package com.mara.identity.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Grants {@code mara_app} a login and this deployment's password, on every startup.
 *
 * <p>{@code V1__tenancy_and_terminals.sql}/{@code V2__row_level_security.sql} create
 * {@code mara_app} as {@code NOLOGIN} — deliberately, since a versioned schema migration
 * should not carry a runtime credential, and Flyway's checksum validation would refuse a
 * migration edited later to change one anyway. Nothing before this class ran ever gave
 * the role a password, which means a genuinely fresh deployment could migrate the schema
 * successfully and then fail every single request, because the role the application
 * connects as cannot log in.
 *
 * <p>This closes that gap without touching a migration: it runs after {@code flyway}
 * (see the {@code @DependsOn} below) and re-applies the configured password every time,
 * so it is safe to run on a database that already has it set, and self-healing if an
 * operator ever rotates {@code MARA_DB_APP_PASSWORD} without a schema change to force it.
 *
 * <p>{@code ALTER ROLE ... PASSWORD} takes a string literal, not a bind parameter — the
 * same restriction {@code SET} has, and the reason
 * {@link com.mara.identity.tenant.TenantAwareDataSource} uses {@code set_config} instead
 * of concatenating a value into SQL text. The same pattern applies here: the password is
 * bound once as a genuine JDBC parameter to {@code set_config}, then read back inside a
 * {@code DO} block via {@code format(..., %L)}, which quotes it correctly for the literal
 * position — so a password containing a quote or backslash cannot break out of the
 * statement. Both statements run inside one explicit transaction so they are guaranteed
 * to share the same session; {@code set_config}'s value would not otherwise be visible to
 * the second statement if a pooled connection handed them to two different sessions.
 */
@Configuration
public class AppRoleLoginConfig {

    @Bean(initMethod = "run")
    @DependsOn("flyway")
    public Runnable appRoleLoginBootstrap(
            @Qualifier("ownerDataSource") DataSource ownerDataSource,
            @Value("${mara.datasource.app.password}") String appPassword) {
        JdbcTemplate ownerJdbc = new JdbcTemplate(ownerDataSource);
        PlatformTransactionManager txManager = new DataSourceTransactionManager(ownerDataSource);
        TransactionTemplate transaction = new TransactionTemplate(txManager);

        return () -> transaction.executeWithoutResult(status -> {
            // set_config is a function call, not DML — it returns its new value as a
            // one-row result set, so this must be queried, never .update()'d; the
            // driver rejects executeUpdate() outright when a statement returns rows.
            ownerJdbc.queryForObject(
                    "SELECT set_config('mara.bootstrap_password', ?, true)", String.class, appPassword);
            ownerJdbc.execute(
                    "DO $$ BEGIN "
                            + "EXECUTE format('ALTER ROLE mara_app WITH LOGIN PASSWORD %L', "
                            + "current_setting('mara.bootstrap_password')); "
                            + "END $$;");
        });
    }
}
