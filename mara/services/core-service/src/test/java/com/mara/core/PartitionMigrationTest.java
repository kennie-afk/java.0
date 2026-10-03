package com.mara.core;

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
 * V3 turns three populated tables into hash partitions. This proves it does so on existing data,
 * as a NON-superuser owner (which FORCE ROW LEVEL SECURITY would have blinded to its own rows,
 * silently dropping them), and that every guarantee survives: append-only on the parent and on a
 * partition named directly, balanced ledger, one fiscal number per sale per tenant, tenant
 * isolation, and no privilege on a partition.
 *
 * <p>Needs {@code -Dmara.test.jdbc.url} pointing at a superuser connection; builds its own
 * scratch database and a role that is not a superuser.
 */
class PartitionMigrationTest {

    private static final String JDBC_URL = System.getProperty("mara.test.jdbc.url");
    private static final String SUPER_USER = System.getProperty("mara.test.owner.user", "mara_owner");
    private static final String SUPER_PASSWORD = System.getProperty("mara.test.owner.password", "owner-secret");

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String DB = "mig_" + SUFFIX;
    private static final String OWNER = "mig_owner_" + SUFFIX;
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
    static void scratchDatabase() throws SQLException {
        Assumptions.assumeTrue(JDBC_URL != null && !JDBC_URL.isBlank(), "needs -Dmara.test.jdbc.url");
        try (Connection c = connect("postgres", SUPER_USER, SUPER_PASSWORD); Statement s = c.createStatement()) {
            s.execute("CREATE ROLE " + OWNER + " LOGIN PASSWORD 'pw' CREATEROLE");
            s.execute("GRANT mara_app TO " + OWNER);
            s.execute("CREATE DATABASE " + DB + " OWNER " + OWNER);
        }
        url = String.format(baseUrl(), DB);
    }

    @AfterAll
    static void dropScratch() throws SQLException {
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

    private static void insertSale(Statement s, String tenant, String terminal, long seq, Long fiscal, boolean accepted)
            throws SQLException {
        s.execute("INSERT INTO ledger_txn (tenant_id, kind, currency, occurred_at, source_terminal, source_sequence) "
                + "VALUES ('" + tenant + "', 'SALE', 'KES', now(), '" + terminal + "', " + seq + ")");
        long txn;
        try (var rs = s.executeQuery("SELECT currval('ledger_txn_id_seq')")) {
            rs.next();
            txn = rs.getLong(1);
        }
        s.execute("INSERT INTO posting (txn_id, tenant_id, account_code, currency, debit_minor) "
                + "VALUES (" + txn + ", '" + tenant + "', 'CASH', 'KES', 1000)");
        s.execute("INSERT INTO posting (txn_id, tenant_id, account_code, currency, credit_minor) "
                + "VALUES (" + txn + ", '" + tenant + "', 'SALES', 'KES', 1000)");
        s.execute("INSERT INTO sale (terminal_id, sequence, tenant_id, occurred_at, currency, total_minor, net_minor, "
                + "tax_minor, applied_minor, fiscal_status, fiscal_number, fiscal_accepted, consistent, txn_id, body) "
                + "VALUES ('" + terminal + "', " + seq + ", '" + tenant + "', now(), 'KES', 1000, 862, 138, 1000, "
                + (fiscal == null ? "'FISCAL_PENDING', NULL" : "'NUMBERED', " + fiscal) + ", " + accepted
                + ", true, " + txn + ", '{}')");
    }

    /** Runs {@code sql}, expects it to fail with {@code fragment}, and rolls the aborted transaction back. */
    private static void refused(Connection c, Statement s, String sql, String fragment) throws SQLException {
        try {
            assertThatThrownBy(() -> s.execute(sql)).hasMessageMatching("(?s).*(" + fragment + ").*");
        } finally {
            c.rollback();
        }
    }

    private static long one(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); var rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void existingRowsSurviveAndEveryGuaranteeStillHolds() throws Exception {
        migrate("2");
        // Populate at the OLD schema, as the (non-superuser) owner, one tenant at a time: the
        // owner is bound by FORCE ROW LEVEL SECURITY exactly as production's would be.
        try (Connection c = connect(DB, OWNER, "pw"); Statement s = c.createStatement()) {
            for (String tenant : new String[] {"TEN-A", "TEN-B", "TEN-C"}) {
                c.setAutoCommit(false);
                s.execute("SELECT set_config('mara.tenant_id', '" + tenant + "', true)");
                s.execute("INSERT INTO account (tenant_id, code, name, kind) VALUES ('" + tenant + "', 'CASH', 'Cash', 'ASSET')");
                s.execute("INSERT INTO account (tenant_id, code, name, kind) VALUES ('" + tenant + "', 'SALES', 'Sales', 'INCOME')");
                for (int i = 1; i <= 7; i++) {
                    insertSale(s, tenant, "TERM-" + tenant, i, (long) i, true);
                }
                c.commit();
            }
        }
        long maxIdBefore;
        try (Connection c = connect(DB, SUPER_USER, SUPER_PASSWORD)) {
            maxIdBefore = one(c, "SELECT max(id) FROM ledger_txn");
            assertThat(one(c, "SELECT count(*) FROM sale")).isEqualTo(21);
        }

        migrate("latest");

        try (Connection c = connect(DB, OWNER, "pw"); Statement s = c.createStatement()) {
            assertThat(one(c, "SELECT count(*) FROM pg_class WHERE relkind = 'p' AND relname IN ('sale','posting','ledger_txn')"))
                    .isEqualTo(3);
            assertThat(one(c, "SELECT count(*) FROM pg_inherits i JOIN pg_class p ON p.oid = i.inhparent WHERE p.relname = 'sale'"))
                    .isEqualTo(16);
            for (String tenant : new String[] {"TEN-A", "TEN-B", "TEN-C"}) {
                s.execute("SELECT set_config('mara.tenant_id', '" + tenant + "', false)");
                assertThat(one(c, "SELECT count(*) FROM sale")).as(tenant + " sales").isEqualTo(7);
                assertThat(one(c, "SELECT count(*) FROM ledger_txn")).as(tenant + " transactions").isEqualTo(7);
                assertThat(one(c, "SELECT count(*) FROM posting")).as(tenant + " postings").isEqualTo(14);
                assertThat(one(c, "SELECT count(*) FROM sale s JOIN ledger_txn t ON t.tenant_id = s.tenant_id AND t.id = s.txn_id"))
                        .as(tenant + " sale -> transaction links").isEqualTo(7);
            }
            // ids were kept and the sequence goes on from where it was
            s.execute("SELECT set_config('mara.tenant_id', 'TEN-A', false)");
            assertThat(one(c, "SELECT max(id) FROM ledger_txn")).isLessThanOrEqualTo(maxIdBefore);
            c.setAutoCommit(false);
            insertSale(s, "TEN-A", "TERM-TEN-A", 99, 99L, true);
            c.commit();
            assertThat(one(c, "SELECT max(id) FROM ledger_txn")).isGreaterThan(maxIdBefore);
        }
    }

    @Test
    void theGuaranteesAreStillEnforcedByTheDatabase() throws Exception {
        migrate("latest");
        try (Connection c = connect(DB, OWNER, "pw"); Statement s = c.createStatement()) {
            c.setAutoCommit(true);
            s.execute("SELECT set_config('mara.tenant_id', 'TEN-G', false)");
            s.execute("INSERT INTO account (tenant_id, code, name, kind) VALUES ('TEN-G', 'CASH', 'Cash', 'ASSET')");
            s.execute("INSERT INTO account (tenant_id, code, name, kind) VALUES ('TEN-G', 'SALES', 'Sales', 'INCOME')");
            c.setAutoCommit(false);
            insertSale(s, "TEN-G", "TERM-G", 1, 1L, true);
            c.commit();

            // append-only, through the parent and through a partition named directly
            refused(c, s, "UPDATE sale SET total_minor = 1", "sale is append-only");
            refused(c, s, "DELETE FROM posting", "posting is append-only");
            refused(c, s, "TRUNCATE sale", "append-only");
            refused(c, s, "TRUNCATE sale_h03", "append-only");
            // referenced by foreign keys, so PostgreSQL refuses it before the trigger even runs: either is a refusal
            refused(c, s, "TRUNCATE ledger_txn_h07", "append-only|cannot truncate a table referenced");
            refused(c, s, "TRUNCATE posting_h11", "append-only");

            // the same terminal sequence cannot post twice
            refused(c, s, "INSERT INTO ledger_txn (tenant_id, kind, currency, occurred_at, source_terminal, source_sequence) "
                    + "VALUES ('TEN-G', 'SALE', 'KES', now(), 'TERM-G', 1)", "tenant_id, source_terminal, source_sequence");
            // one accepted sale per fiscal number per tenant
            try {
                assertThatThrownBy(() -> insertSale(s, "TEN-G", "TERM-G2", 1, 1L, true))
                        .hasMessageContaining("(tenant_id, fiscal_number)");
            } finally {
                c.rollback();
            }

            // an unbalanced transaction is refused at commit
            s.execute("INSERT INTO ledger_txn (tenant_id, kind, currency, occurred_at) VALUES ('TEN-G', 'SALE', 'KES', now())");
            long txn = one(c, "SELECT currval('ledger_txn_id_seq')");
            s.execute("INSERT INTO posting (txn_id, tenant_id, account_code, currency, debit_minor) VALUES (" + txn + ", 'TEN-G', 'CASH', 'KES', 500)");
            s.execute("INSERT INTO posting (txn_id, tenant_id, account_code, currency, credit_minor) VALUES (" + txn + ", 'TEN-G', 'SALES', 'KES', 400)");
            try {
                assertThatThrownBy(c::commit).hasMessageContaining("unbalanced");
            } finally {
                c.rollback();
            }

            // a posting cannot reference another tenant's transaction
            s.execute("SELECT set_config('mara.tenant_id', 'TEN-H', false)");
            refused(c, s, "INSERT INTO posting (txn_id, tenant_id, account_code, currency, debit_minor) "
                    + "VALUES (1, 'TEN-H', 'CASH', 'KES', 1)", "violates");
        }
        // mara_app: sees only its tenant, nothing without one, and has no privilege on a partition
        try (Connection c = connect(DB, OWNER, "pw"); Statement s = c.createStatement()) {
            s.execute("SET ROLE mara_app");
            assertThat(one(c, "SELECT count(*) FROM sale")).as("no tenant bound").isZero();
            s.execute("SELECT set_config('mara.tenant_id', 'TEN-G', false)");
            assertThat(one(c, "SELECT count(*) FROM sale")).isEqualTo(1);
            s.execute("SELECT set_config('mara.tenant_id', 'TEN-OTHER', false)");
            assertThat(one(c, "SELECT count(*) FROM sale")).isZero();
            assertThatThrownBy(() -> s.execute("SELECT * FROM sale_h00")).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> s.execute("SELECT * FROM ledger_txn_h00")).hasMessageContaining("permission denied");
        }
    }
}
