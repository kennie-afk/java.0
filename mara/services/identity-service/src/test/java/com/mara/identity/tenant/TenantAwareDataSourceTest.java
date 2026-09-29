package com.mara.identity.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Proves that {@link TenantAwareDataSource} actually does what its own Javadoc claims:
 * that a connection handed to application code carries the tenant a caller bound via
 * {@link TenantContext} <em>before</em> a Spring-managed transaction started — which is
 * exactly the order {@code TenantFilter} and {@code @Transactional} produce in
 * production, since the filter runs first and sets the context before the controller
 * method (and its transaction) begins.
 *
 * <p>This is the one path {@code EnrolmentService} never exercises, because it calls
 * {@code EnrolmentRepository.bindTenant} explicitly, mid-transaction, as a workaround.
 * Any future endpoint that reads tenant-scoped data without that explicit re-bind —
 * which the class's own contract says should never be necessary — would otherwise see
 * silence: row-level security does not error on an unmatched policy, it just returns no
 * rows, so this bug fails silently instead of loudly.
 */
@DisplayName("TenantAwareDataSource: automatic binding vs. transaction timing")
class TenantAwareDataSourceTest {

    private static final String EXTERNAL_URL = System.getProperty("mara.test.jdbc.url");

    private static PostgreSQLContainer<?> postgres;
    private static DataSource ownerDataSource;
    private static String jdbcUrl;
    private static String ownerUser;
    private static String ownerPassword;

    private JdbcTemplate ownerJdbc;
    private JdbcTemplate appJdbc;
    private TransactionTemplate transaction;

    @BeforeAll
    static void startDatabase() {
        if (EXTERNAL_URL != null && !EXTERNAL_URL.isBlank()) {
            jdbcUrl = EXTERNAL_URL;
            ownerUser = System.getProperty("mara.test.owner.user", "mara_owner");
            ownerPassword = System.getProperty("mara.test.owner.password", "owner-secret");
        } else {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("mara_identity")
                    .withUsername("mara_owner")
                    .withPassword("owner-secret");
            postgres.start();
            jdbcUrl = postgres.getJdbcUrl();
            ownerUser = postgres.getUsername();
            ownerPassword = postgres.getPassword();
        }

        ownerDataSource = dataSourceFor(ownerUser, ownerPassword);
        Flyway.configure()
                .dataSource(ownerDataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        new JdbcTemplate(ownerDataSource).execute(
                "ALTER ROLE mara_app WITH LOGIN PASSWORD 'app-secret'");
    }

    @AfterAll
    static void stopDatabase() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void wireDataSources() {
        ownerJdbc = new JdbcTemplate(ownerDataSource);
        ownerJdbc.execute("TRUNCATE enrolment_code, terminal, staff, branch, tenant CASCADE");

        ownerJdbc.update(
                "INSERT INTO tenant (id, legal_name, trading_name, country_code, default_currency) "
                        + "VALUES ('TEN-A', 'A Ltd', 'A', 'KE', 'KES')");
        ownerJdbc.update(
                "INSERT INTO branch (id, tenant_id, name, timezone) "
                        + "VALUES ('BR-A', 'TEN-A', 'Main', 'Africa/Nairobi')");

        DataSource appDataSource = new TenantAwareDataSource(dataSourceFor("mara_app", "app-secret"));
        appJdbc = new JdbcTemplate(appDataSource);

        PlatformTransactionManager txManager = new DataSourceTransactionManager(appDataSource);
        transaction = new TransactionTemplate(txManager);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a tenant bound before the transaction begins is still visible inside it")
    void bindingSetBeforeTheTransactionSurvivesIntoIt() {
        // This is the production order: TenantFilter runs first (outside any
        // transaction) and sets TenantContext; the @Transactional service method
        // — and the transaction it opens — starts afterwards. No explicit re-bind,
        // because TenantAwareDataSource's whole point is that none should be needed.
        TenantContext.set("TEN-A");

        Boolean visible = transaction.execute(status -> appJdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM branch WHERE tenant_id = 'TEN-A')", Boolean.class));

        assertThat(visible)
                .as("row-level security should show TEN-A's own branch to a connection "
                        + "that had TEN-A bound before its transaction started")
                .isTrue();
    }

    @Test
    @DisplayName("an unbound request still sees nothing, tenant isolation intact either way")
    void noTenantBoundSeesNothing() {
        Boolean visible = transaction.execute(status -> appJdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM branch WHERE tenant_id = 'TEN-A')", Boolean.class));

        assertThat(visible).isFalse();
    }

    @Test
    @DisplayName("binding does not leak to a later transaction on a reused connection")
    void doesNotLeakAcrossTransactions() {
        TenantContext.set("TEN-A");
        Boolean firstTenantSees = transaction.execute(status -> appJdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM branch WHERE tenant_id = 'TEN-A')", Boolean.class));
        assertThat(firstTenantSees).isTrue();

        // A second, unrelated request on a thread that never set a tenant.
        TenantContext.clear();
        Boolean unboundSees = transaction.execute(status -> appJdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM branch WHERE tenant_id = 'TEN-A')", Boolean.class));
        assertThat(unboundSees)
                .as("a request that bound no tenant must never see TEN-A's data just because "
                        + "a pooled connection previously served TEN-A")
                .isFalse();
    }

    private static DataSource dataSourceFor(String username, String password) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(jdbcUrl);
        ds.setUser(username);
        ds.setPassword(password);
        return ds;
    }
}
