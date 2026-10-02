package com.mara.kit.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Creates the service's own database when it does not exist yet.
 *
 * <p>Each Mara service owns a database (identity holds the trust root and shares it with
 * nothing). On a fresh Postgres the later services' databases do not exist, and on an old
 * volume an init script would never run again, so the owner role creates it on start.
 * The name is validated, not quoted-and-hoped: it comes from the JDBC URL the operator
 * configured, and {@code CREATE DATABASE} cannot take a bind parameter.
 */
public final class DatabaseBootstrap {

    private DatabaseBootstrap() {
    }

    public static void ensureExists(String jdbcUrl, String user, String password) {
        int slash = jdbcUrl.indexOf('/', "jdbc:postgresql://".length());
        if (slash < 0) {
            return;
        }
        String rest = jdbcUrl.substring(slash + 1);
        int q = rest.indexOf('?');
        String name = q < 0 ? rest : rest.substring(0, q);
        String params = q < 0 ? "" : rest.substring(q);
        if (!name.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalStateException("refusing to create database with unusual name: " + name);
        }
        String maintenanceUrl = jdbcUrl.substring(0, slash + 1) + "postgres" + params;
        try (Connection c = DriverManager.getConnection(maintenanceUrl, user, password)) {
            try (PreparedStatement s = c.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
                s.setString(1, name);
                try (ResultSet rs = s.executeQuery()) {
                    if (rs.next()) {
                        return;
                    }
                }
            }
            try (var stmt = c.createStatement()) {
                stmt.execute("CREATE DATABASE " + name);
            } catch (SQLException e) {
                // Two replicas starting together: the loser's CREATE fails because the winner's
                // succeeded. 42P04 is duplicate_database; anything else is real.
                if (!"42P04".equals(e.getSQLState())) {
                    throw e;
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not ensure database " + name + " exists", e);
        }
    }
}
