package com.mara.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The opt-in script hash-partitions journal_entry. Proves it on existing rows as a NON-superuser owner (whom
 * FORCE ROW LEVEL SECURITY would blind to the table, so a naive copy-and-drop loses everything),
 * and that the append-only guarantee holds on the parent and on a partition named directly.
 * Needs {@code -Dmara.test.jdbc.url} to a superuser connection; builds its own scratch database.
 */
class PartitionMigrationTest {

    private static final String JDBC_URL = System.getProperty("mara.test.jdbc.url");
    private static final String SUPER_USER = System.getProperty("mara.test.owner.user", "mara_owner");
    private static final String SUPER_PASSWORD = System.getProperty("mara.test.owner.password", "owner-secret");
    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String DB = "migs_" + SUFFIX;
    private static final String OWNER = "migs_owner_" + SUFFIX;
    private static String url;

    private static String baseUrl() {
        int slash = JDBC_URL.indexOf('/', "jdbc:postgresql://".length());
        int query = JDBC_URL.indexOf('?', slash);
        return JDBC_URL.substring(0, slash + 1) + "%s" + (query > 0 ? JDBC_URL.substring(query) : "");
    }

    private static Connection connect(String db, String user, String password) throws SQLException {
        return DriverManager.getConnection(String.format(baseUrl(), db), user, password);
    }

    @BeforeAll
    static void scratch() throws SQLException {
        Assumptions.assumeTrue(JDBC_URL != null && !JDBC_URL.isBlank(), "needs -Dmara.test.jdbc.url");
        try (Connection c = connect("postgres", SUPER_USER, SUPER_PASSWORD); Statement s = c.createStatement()) {
            s.execute("CREATE ROLE " + OWNER + " LOGIN PASSWORD 'pw' CREATEROLE");
            s.execute("GRANT mara_app TO " + OWNER);
            s.execute("CREATE DATABASE " + DB + " OWNER " + OWNER);
        }
        url = String.format(baseUrl(), DB);
    }

    @AfterAll
    static void drop() throws SQLException {
        if (JDBC_URL == null || JDBC_URL.isBlank()) {
            return;
        }
        try (Connection c = connect("postgres", SUPER_USER, SUPER_PASSWORD); Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + DB + " WITH (FORCE)");
            s.execute("DROP ROLE IF EXISTS " + OWNER);
        }
    }

    private static void migrate(String target) {
        Flyway.configure().dataSource(url, OWNER, "pw").locations("classpath:db/migration").target(target)
                .load().migrate();
    }

    /** The opt-in script, run the way an operator would: one transaction, as the owner. */
    private static void applyPartitioning() throws Exception {
        String sql;
        try (var in = PartitionMigrationTest.class.getResourceAsStream("/db/optional/partition-journal.sql")) {
            sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        try (Connection c = connect(DB, OWNER, "pw")) {
            if (one(c, "SELECT count(*) FROM pg_class WHERE relkind = 'p' AND relname = 'journal_entry'") == 1) {
                return;   // another test in this class already did it to the shared scratch database
            }
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.execute(sql);
            }
            c.commit();
        }
    }

    private static void insertEntry(Statement s, String tenant, String terminal, int seq) throws SQLException {
        String d = "decode(repeat('0" + (seq % 10) + "', 32), 'hex')";
        s.execute("INSERT INTO journal_entry (terminal_id, sequence, tenant_id, epoch_second, nano, sale, body_digest, "
                + "previous_digest, digest, signature) VALUES ('" + terminal + "', " + seq + ", '" + tenant + "', 1, 0, '{}', "
                + d + ", " + d + ", " + d + ", decode('00', 'hex'))");
    }

    private static long one(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); var rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void existingEntriesSurviveAndStayAppendOnlyAndTenantScoped() throws Exception {
        migrate("latest");
        try (Connection c = connect(DB, OWNER, "pw"); Statement s = c.createStatement()) {
            for (String tenant : new String[] {"TEN-A", "TEN-B"}) {
                c.setAutoCommit(false);
                s.execute("SELECT set_config('mara.tenant_id', '" + tenant + "', true)");
                for (int t = 1; t <= 3; t++) {
                    for (int seq = 1; seq <= 5; seq++) {
                        insertEntry(s, tenant, "TERM-" + tenant + "-" + t, seq);
                    }
                }
                c.commit();
            }
        }
        applyPartitioning();

        try (Connection c = connect(DB, OWNER, "pw"); Statement s = c.createStatement()) {
            c.setAutoCommit(true);
            assertThat(one(c, "SELECT count(*) FROM pg_class WHERE relkind = 'p' AND relname = 'journal_entry'")).isEqualTo(1);
            assertThat(one(c, "SELECT count(*) FROM pg_inherits i JOIN pg_class p ON p.oid = i.inhparent WHERE p.relname = 'journal_entry'"))
                    .isEqualTo(16);
            for (String tenant : new String[] {"TEN-A", "TEN-B"}) {
                s.execute("SELECT set_config('mara.tenant_id', '" + tenant + "', false)");
                assertThat(one(c, "SELECT count(*) FROM journal_entry")).as(tenant).isEqualTo(15);
            }
            // a terminal's sequence is still unique in the database
            s.execute("SELECT set_config('mara.tenant_id', 'TEN-A', false)");
            assertThatThrownBy(() -> insertEntry(s, "TEN-A", "TERM-TEN-A-1", 3)).hasMessageContaining("duplicate key");
            // append-only on the parent, and on a partition named directly
            assertThatThrownBy(() -> s.execute("UPDATE journal_entry SET nano = 1")).hasMessageContaining("append-only");
            assertThatThrownBy(() -> s.execute("DELETE FROM journal_entry")).hasMessageContaining("append-only");
            assertThatThrownBy(() -> s.execute("TRUNCATE journal_entry")).hasMessageContaining("append-only");
            assertThatThrownBy(() -> s.execute("TRUNCATE journal_entry_h05")).hasMessageContaining("append-only");
        }
        try (Connection c = connect(DB, OWNER, "pw"); Statement s = c.createStatement()) {
            s.execute("SET ROLE mara_app");
            assertThat(one(c, "SELECT count(*) FROM journal_entry")).as("no tenant bound").isZero();
            s.execute("SELECT set_config('mara.tenant_id', 'TEN-B', false)");
            assertThat(one(c, "SELECT count(*) FROM journal_entry")).isEqualTo(15);
            assertThatThrownBy(() -> s.execute("SELECT * FROM journal_entry_h00")).hasMessageContaining("permission denied");
        }
    }
}
