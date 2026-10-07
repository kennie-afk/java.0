package com.soko.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Many threads, separate connections, real commits: whatever the application does on top, the
 * database must let exactly one order through for a tenant's idempotency key. Skipped when no
 * Postgres answers (see TenantIsolationDatabaseTest).
 */
class IdempotencyConcurrencyDatabaseTest {

    private static final String HOST_URL =
            System.getenv().getOrDefault("SOKO_TEST_ADMIN_URL", "jdbc:postgresql://localhost:55437/");
    private static final String ADMIN = System.getenv().getOrDefault("SOKO_TEST_ADMIN_USER", "postgres");
    private static final String ADMIN_PW = System.getenv().getOrDefault("SOKO_TEST_ADMIN_PASSWORD", "ownerpw");
    private static final String DB = "soko_idem_it";

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();

    @BeforeAll
    static void migrate() throws Exception {
        try (Connection admin = DriverManager.getConnection(HOST_URL + "postgres", ADMIN, ADMIN_PW);
                Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + DB);
        } catch (SQLException unavailable) {
            assumeTrue(false, "no Postgres at " + HOST_URL + " (" + unavailable.getMessage() + ")");
        }
        Flyway.configure().dataSource(HOST_URL + DB, ADMIN, ADMIN_PW)
                .locations("classpath:db/migration")
                .placeholders(Map.of("soko_app_password", "app-password-for-tests-1",
                        "soko_system_key", "system-key-for-tests-0001"))
                .load().migrate();
        try (Connection c = DriverManager.getConnection(HOST_URL + DB, ADMIN, ADMIN_PW)) {
            exec(c, "insert into tenants (id, name, slug) values (?, 'T', 't-idem')", TENANT);
            exec(c, "insert into customers (id, tenant_id, name, phone, county) values (?, ?, 'C', '1', 'x')",
                    CUSTOMER, TENANT);
        }
    }

    private static void exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        }
    }

    @Test
    void sixteenSimultaneousInsertsWithOneKeyYieldExactlyOneOrder() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String reference = "SO-RACE-" + i;
            results.add(pool.submit(() -> {
                try (Connection c = DriverManager.getConnection(HOST_URL + DB, ADMIN, ADMIN_PW)) {
                    go.await();
                    exec(c, "insert into orders (tenant_id, reference, customer_id, idempotency_key) values (?, ?, ?, 'one-key')",
                            TENANT, reference, CUSTOMER);
                    return true;
                } catch (SQLException duplicate) {
                    return "23505".equals(duplicate.getSQLState()) ? false : rethrow(duplicate);
                }
            }));
        }
        go.countDown();
        long inserted = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) {
                inserted++;
            }
        }
        pool.shutdown();

        assertThat(inserted).isEqualTo(1);
        try (Connection c = DriverManager.getConnection(HOST_URL + DB, ADMIN, ADMIN_PW);
                PreparedStatement ps = c.prepareStatement("select count(*) from orders where idempotency_key = 'one-key'");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getLong(1)).isEqualTo(1);
        }
    }

    private static boolean rethrow(SQLException e) {
        throw new IllegalStateException(e);
    }
}
