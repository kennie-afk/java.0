package com.soko.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Proves tenant isolation where it is enforced: in the database, as the role the application
 * really runs as. Applies the real migrations to a scratch database, then talks to it as
 * {@code soko_app} with raw SQL, so a forgotten {@code tenant_id} filter in application code cannot
 * hide a gap.
 *
 * <p>Needs a Postgres superuser to create the scratch database. Defaults to a local container:
 * {@code docker run -d --name soko-it-pg -e POSTGRES_PASSWORD=ownerpw -p 55437:5432 postgres:16-alpine};
 * override with SOKO_TEST_ADMIN_URL / SOKO_TEST_ADMIN_USER / SOKO_TEST_ADMIN_PASSWORD. Skipped (not
 * failed) when no database answers.
 */
class TenantIsolationDatabaseTest {

    private static final String HOST_URL =
            System.getenv().getOrDefault("SOKO_TEST_ADMIN_URL", "jdbc:postgresql://localhost:55437/");
    private static final String ADMIN = System.getenv().getOrDefault("SOKO_TEST_ADMIN_USER", "postgres");
    private static final String ADMIN_PW = System.getenv().getOrDefault("SOKO_TEST_ADMIN_PASSWORD", "ownerpw");
    private static final String DB = "soko_it";
    private static final String APP_PW = "app-password-for-tests-1";
    private static final String SYSTEM_KEY = "system-key-for-tests-0001";

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    private static boolean ready;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        try (Connection admin = DriverManager.getConnection(HOST_URL + "postgres", ADMIN, ADMIN_PW);
                Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + DB);
        } catch (SQLException unavailable) {
            assumeTrue(false, "no Postgres at " + HOST_URL + " (" + unavailable.getMessage() + ")");
        }
        Flyway.configure()
                .dataSource(HOST_URL + DB, ADMIN, ADMIN_PW)
                .locations("classpath:db/migration")
                .placeholders(Map.of("soko_app_password", APP_PW, "soko_system_key", SYSTEM_KEY))
                .load()
                .migrate();

        // The owner here is a superuser, so it bypasses row-level security: fine for seeding.
        try (Connection owner = DriverManager.getConnection(HOST_URL + DB, ADMIN, ADMIN_PW)) {
            for (UUID tenant : new UUID[] {TENANT_A, TENANT_B}) {
                exec(owner, "INSERT INTO tenants (id, name, slug) VALUES (?, ?, ?)",
                        tenant, "Tenant " + tenant, "t-" + tenant.toString().substring(0, 8));
                exec(owner, "INSERT INTO users (tenant_id, email, full_name, password_hash) VALUES (?, ?, 'U', 'x')",
                        tenant, "u-" + tenant + "@test.local");
                exec(owner, "INSERT INTO products (tenant_id, sku, name, category, unit, shelf_life_hours, list_price_cents)"
                        + " VALUES (?, 'SKU1', 'Tomatoes', 'veg', 'kg', 48, 10000)", tenant);
            }
        }
        ready = true;
    }

    private static void exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        }
    }

    private static Connection app() throws SQLException {
        Connection c = DriverManager.getConnection(HOST_URL + DB, "soko_app", APP_PW);
        c.setAutoCommit(false);
        return c;
    }

    private static void bind(Connection c, UUID tenant, String systemKey) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT set_config('soko.tenant_id', ?, true), set_config('soko.system_key', ?, true)")) {
            ps.setString(1, tenant == null ? "" : tenant.toString());
            ps.setString(2, systemKey);
            ps.execute();
        }
    }

    private static long count(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT count(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void theApplicationRoleCannotBypassRowLevelSecurity() throws Exception {
        assumeTrue(ready);
        try (Connection c = app(); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'soko_app'")) {
            rs.next();
            assertThat(rs.getBoolean(1)).isFalse();
            assertThat(rs.getBoolean(2)).isFalse();
        }
    }

    @Test
    void aTenantSeesOnlyItsOwnRowsInEveryTenantTable() throws Exception {
        assumeTrue(ready);
        try (Connection c = app()) {
            bind(c, TENANT_A, "");
            assertThat(count(c, "users")).isEqualTo(1);
            assertThat(count(c, "products")).isEqualTo(1);
            try (Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery("SELECT DISTINCT tenant_id FROM products")) {
                rs.next();
                assertThat(rs.getObject(1, UUID.class)).isEqualTo(TENANT_A);
            }
            c.rollback();
        }
    }

    @Test
    void withNoTenantBoundTheRoleSeesNothing() throws Exception {
        assumeTrue(ready);
        try (Connection c = app()) {
            assertThat(count(c, "users")).isZero();
            assertThat(count(c, "products")).isZero();
            c.rollback();
        }
    }

    @Test
    void aTenantCannotWriteARowForAnotherTenant() throws Exception {
        assumeTrue(ready);
        try (Connection c = app()) {
            bind(c, TENANT_A, "");
            assertThatThrownBy(() -> exec(c,
                    "INSERT INTO products (tenant_id, sku, name, category, unit, shelf_life_hours, list_price_cents)"
                            + " VALUES (?, 'LEAK', 'x', 'veg', 'kg', 1, 1)", TENANT_B))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security");
            c.rollback();
        }
    }

    @Test
    void aTenantCannotUpdateOrDeleteAnotherTenantsRows() throws Exception {
        assumeTrue(ready);
        try (Connection c = app()) {
            bind(c, TENANT_A, "");
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE products SET name = 'hacked' WHERE tenant_id = ?")) {
                ps.setObject(1, TENANT_B);
                assertThat(ps.executeUpdate()).isZero();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE tenant_id = ?")) {
                ps.setObject(1, TENANT_B);
                assertThat(ps.executeUpdate()).isZero();
            }
            c.rollback();
        }
    }

    @Test
    void theSystemKeyWidensAccessButAWrongKeyDoesNot() throws Exception {
        assumeTrue(ready);
        try (Connection c = app()) {
            bind(c, null, SYSTEM_KEY);
            assertThat(count(c, "users")).isGreaterThanOrEqualTo(2);
            c.rollback();
            bind(c, null, "a-guessed-key-that-is-wrong");
            assertThat(count(c, "users")).isZero();
            c.rollback();
        }
    }

    @Test
    void theApplicationRoleCannotReadTheSystemKeyHash() throws Exception {
        assumeTrue(ready);
        try (Connection c = app(); Statement st = c.createStatement()) {
            assertThatThrownBy(() -> st.executeQuery("SELECT * FROM soko_system_key"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
            c.rollback();
        }
    }
}
