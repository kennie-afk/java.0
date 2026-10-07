package com.soko.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Two limiter instances over one table stand in for two replicas. Skipped when no Postgres answers. */
class SharedWindowLimiterDatabaseTest {

    private static final String HOST_URL =
            System.getenv().getOrDefault("SOKO_TEST_ADMIN_URL", "jdbc:postgresql://localhost:55437/");
    private static final String ADMIN = System.getenv().getOrDefault("SOKO_TEST_ADMIN_USER", "postgres");
    private static final String ADMIN_PW = System.getenv().getOrDefault("SOKO_TEST_ADMIN_PASSWORD", "ownerpw");
    private static final String DB = "soko_ratelimit_it";

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void database() throws Exception {
        try (Connection c = DriverManager.getConnection(HOST_URL + "postgres", ADMIN, ADMIN_PW);
                Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + DB);
            st.execute("CREATE DATABASE " + DB);
        } catch (Exception noDatabase) {
            assumeTrue(false, "no Postgres reachable: " + noDatabase.getMessage());
        }
        jdbc = new JdbcTemplate(new DriverManagerDataSource(HOST_URL + DB, ADMIN, ADMIN_PW));
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V11__rate_limit_hits.sql")));
    }

    @Test
    void theLimitHoldsAcrossTwoReplicasAndResetsWithTheWindow() {
        AtomicLong now = new AtomicLong(5_000_000L);
        RateLimiter a = new SharedWindowLimiter(jdbc, "login", 3, 60_000L, now::get);
        RateLimiter b = new SharedWindowLimiter(jdbc, "login", 3, 60_000L, now::get);

        assertThat(a.hit("1.2.3.4").allowed()).isTrue();
        assertThat(b.hit("1.2.3.4").allowed()).isTrue();
        assertThat(a.hit("1.2.3.4").allowed()).isTrue();
        RateLimiter.Decision refused = b.hit("1.2.3.4");
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfterSeconds()).isBetween(1L, 60L);
        assertThat(b.hit("5.6.7.8").allowed()).isTrue();

        now.addAndGet(60_000L);
        assertThat(a.hit("1.2.3.4").allowed()).isTrue();
    }

    @Test
    void anUnreachableDatabaseFallsBackToTheProcessCounter() {
        JdbcTemplate broken = new JdbcTemplate(new DriverManagerDataSource(HOST_URL + "no_such_db", ADMIN, ADMIN_PW));
        RateLimiter l = new SharedWindowLimiter(broken, "login", 1, 60_000L, System::currentTimeMillis);
        assertThat(l.hit("x").allowed()).isTrue();
        assertThat(l.hit("x").allowed()).isFalse();
    }
}
