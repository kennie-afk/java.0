package com.mara.identity.enrolment;

import static org.assertj.core.api.Assertions.assertThat;

import com.mara.identity.tenant.TenantAwareDataSource;
import com.mara.platform.identity.EnrolmentCode;
import com.mara.platform.identity.EnrolmentOutcome;
import com.mara.platform.identity.EnrolmentPolicy;
import java.security.KeyPairGenerator;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Exercises enrolment against a real PostgreSQL, because the properties that matter here
 * are enforced by the database, not by Java. A mock would prove the code calls a function;
 * only the real thing proves the function refuses a second redemption.
 */
@DisplayName("enrolment, end to end")
class EnrolmentIntegrationTest {

    /**
     * Where the test database comes from.
     *
     * <p>Testcontainers when Docker is reachable; otherwise an externally supplied
     * database via {@code -Dmara.test.jdbc.url}. The build here runs Maven inside a
     * container that cannot see the daemon, so the properties below are proven against
     * a sibling Postgres instead. Same database, same migrations, same assertions —
     * only the lifecycle owner differs.
     */
    private static final String EXTERNAL_URL = System.getProperty("mara.test.jdbc.url");

    private static PostgreSQLContainer<?> postgres;
    private static DataSource ownerDataSource;
    private static String jdbcUrl;
    private static String ownerUser;
    private static String ownerPassword;

    private JdbcTemplate ownerJdbc;
    private EnrolmentService service;
    private EnrolmentRepository repository;

    /**
     * Enrolment must run as ONE transaction: redemption and the terminal insert either
     * both happen or neither. In production {@code @Transactional} on
     * {@link EnrolmentService#enrol} provides that. This test constructs the service by
     * hand, so the proxy is absent and the boundary has to be supplied explicitly —
     * otherwise each statement autocommits and the deferred foreign key is checked
     * before the terminal exists.
     */
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

        // The migration creates mara_app NOLOGIN; a deployment grants it credentials.
        // Do that here so the test can connect as the role production will use — which
        // is the whole point, since mara_app is the role that cannot bypass RLS.
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
    void wireService() {
        ownerJdbc = new JdbcTemplate(ownerDataSource);
        ownerJdbc.execute("TRUNCATE enrolment_code, terminal, staff, branch, tenant CASCADE");

        seedTenant("TEN-A", 4);

        // The application connects as mara_app, which cannot bypass RLS — the same way
        // it will in production.
        DataSource appDataSource =
                new TenantAwareDataSource(dataSourceFor("mara_app", "app-secret"));
        repository = new EnrolmentRepository(new JdbcTemplate(appDataSource));
        service = new EnrolmentService(
                repository,
                new EnrolmentPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        PlatformTransactionManager txManager = new DataSourceTransactionManager(appDataSource);
        transaction = new TransactionTemplate(txManager);
    }

    private static final Instant NOW = Instant.parse("2026-09-07T08:00:00Z");

    @Test
    @DisplayName("a valid code enrols exactly one terminal")
    void enrolsATerminal() {
        String code = issueCode("ENR-1", "TEN-A", NOW, NOW.plus(Duration.ofMinutes(15)));

        EnrolmentOutcome outcome = enrol(code, freshKey(), "Lane 4");

        assertThat(outcome.accepted()).isTrue();
        assertThat(outcome.terminalId()).startsWith("TERM-");

        Integer terminals = ownerJdbc.queryForObject(
                "SELECT count(*) FROM terminal WHERE tenant_id = 'TEN-A'", Integer.class);
        assertThat(terminals).isEqualTo(1);

        Boolean redeemed = ownerJdbc.queryForObject(
                "SELECT redeemed_at IS NOT NULL FROM enrolment_code WHERE id = 'ENR-1'",
                Boolean.class);
        assertThat(redeemed).isTrue();
    }

    @Test
    @DisplayName("the same code cannot enrol a second terminal")
    void refusesASecondUse() {
        String code = issueCode("ENR-1", "TEN-A", NOW, NOW.plus(Duration.ofMinutes(15)));
        enrol(code, freshKey(), "Lane 4");

        EnrolmentOutcome second = enrol(code, freshKey(), "Lane 5");

        assertThat(second.accepted()).isFalse();
        assertThat(second.reason()).isEqualTo(EnrolmentOutcome.Reason.ALREADY_REDEEMED);
        assertThat(countTerminals()).isEqualTo(1);
    }

    @Test
    @DisplayName("two devices racing one code: exactly one wins")
    void survivesAConcurrentRace() throws Exception {
        // The failure this guards against is a read-then-write window: both devices
        // resolve the code as unredeemed, both pass policy, both enrol. The conditional
        // UPDATE inside redeem_enrolment is what closes it.
        String code = issueCode("ENR-1", "TEN-A", NOW, NOW.plus(Duration.ofMinutes(15)));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<EnrolmentOutcome> attempt = () -> enrol(code, freshKey(), "Lane");
            List<Future<EnrolmentOutcome>> results = pool.invokeAll(List.of(attempt, attempt));

            long accepted = results.stream().filter(f -> {
                try {
                    return f.get().accepted();
                } catch (Exception e) {
                    return false;
                }
            }).count();

            assertThat(accepted).as("exactly one device may win the code").isEqualTo(1L);
            assertThat(countTerminals()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("an expired code is refused")
    void refusesAnExpiredCode() {
        String code = issueCode("ENR-1", "TEN-A",
                NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofMinutes(45)));

        EnrolmentOutcome outcome = enrol(code, freshKey(), "Lane 4");

        assertThat(outcome.accepted()).isFalse();
        assertThat(countTerminals()).isZero();
    }

    @Test
    @DisplayName("a code nobody issued is refused")
    void refusesAnUnknownCode() {
        EnrolmentOutcome outcome = enrol(EnrolmentCode.generate(), freshKey(), "Lane 4");

        assertThat(outcome.accepted()).isFalse();
        assertThat(outcome.reason()).isEqualTo(EnrolmentOutcome.Reason.UNKNOWN_CODE);
    }

    @Test
    @DisplayName("a cloned device reusing a registered key is refused")
    void refusesADuplicateKey() {
        String key = freshKey();
        enrol(issueCode("ENR-1", "TEN-A", NOW, NOW.plus(Duration.ofMinutes(15))), key, "Lane 4");

        EnrolmentOutcome clone = enrol(
                issueCode("ENR-2", "TEN-A", NOW, NOW.plus(Duration.ofMinutes(15))), key, "Lane 5");

        assertThat(clone.accepted()).isFalse();
        assertThat(clone.reason()).isEqualTo(EnrolmentOutcome.Reason.KEY_ALREADY_REGISTERED);
        assertThat(countTerminals()).isEqualTo(1);
    }

    @Test
    @DisplayName("enrolment stops at the licensed terminal count")
    void refusesBeyondTheLicence() {
        seedTenant("TEN-SMALL", 1);
        enrol(issueCode("ENR-1", "TEN-SMALL", NOW, NOW.plus(Duration.ofMinutes(15))),
                freshKey(), "Lane 1");

        EnrolmentOutcome beyond = enrol(
                issueCode("ENR-2", "TEN-SMALL", NOW, NOW.plus(Duration.ofMinutes(15))),
                freshKey(), "Lane 2");

        assertThat(beyond.accepted()).isFalse();
        assertThat(beyond.reason()).isEqualTo(EnrolmentOutcome.Reason.TERMINAL_LIMIT_REACHED);
    }

    @Test
    @DisplayName("the plaintext code is never stored")
    void storesOnlyTheHash() {
        String code = issueCode("ENR-1", "TEN-A", NOW, NOW.plus(Duration.ofMinutes(15)));

        byte[] stored = ownerJdbc.queryForObject(
                "SELECT code_hash FROM enrolment_code WHERE id = 'ENR-1'", byte[].class);

        assertThat(stored).isEqualTo(EnrolmentCode.hash(code));
        assertThat(new String(stored)).doesNotContain(EnrolmentCode.normalise(code));
    }

    /** Runs one enrolment inside a transaction, the way the proxy does in production. */
    private EnrolmentOutcome enrol(String code, String publicKey, String label) {
        return transaction.execute(status -> service.enrol(code, publicKey, label));
    }

    // ------------------------------------------------------------------ helpers ---

    private int countTerminals() {
        Integer count = ownerJdbc.queryForObject("SELECT count(*) FROM terminal", Integer.class);
        return count == null ? 0 : count;
    }

    private void seedTenant(String tenantId, int licensedTerminals) {
        ownerJdbc.update(
                "INSERT INTO tenant (id, legal_name, trading_name, country_code, default_currency, "
                        + "licensed_terminals) VALUES (?, ?, ?, 'KE', 'KES', ?)",
                tenantId, tenantId + " Ltd", tenantId, licensedTerminals);
        ownerJdbc.update(
                "INSERT INTO branch (id, tenant_id, name, timezone) VALUES (?, ?, 'Main', 'Africa/Nairobi')",
                "BR-" + tenantId, tenantId);
        ownerJdbc.update(
                "INSERT INTO staff (id, tenant_id, branch_id, display_name, role, pin_hash) "
                        + "VALUES (?, ?, ?, 'Owner', 'OWNER', '$argon2id$stub')",
                "ST-" + tenantId, tenantId, "BR-" + tenantId);
    }

    private String issueCode(String id, String tenantId, Instant issuedAt, Instant expiresAt) {
        String code = EnrolmentCode.generate();
        ownerJdbc.update(
                "INSERT INTO enrolment_code (id, tenant_id, branch_id, code_hash, issued_by, "
                        + "issued_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, "BR-" + tenantId, EnrolmentCode.hash(code),
                "ST-" + tenantId, Timestamp.from(issuedAt), Timestamp.from(expiresAt));
        return code;
    }

    private static String freshKey() {
        try {
            return Base64.getEncoder().encodeToString(
                    KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static DataSource dataSourceFor(String username, String password) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(jdbcUrl);
        ds.setUser(username);
        ds.setPassword(password);
        return ds;
    }
}
