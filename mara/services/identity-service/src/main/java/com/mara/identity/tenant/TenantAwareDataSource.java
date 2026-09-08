package com.mara.identity.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Wraps the pooled {@code DataSource} so every connection handed to the application
 * already carries the current tenant in the session variable that row-level security
 * reads.
 *
 * <p>This is the join between {@link TenantContext} and the policies in
 * {@code V2__row_level_security.sql}. Without it the policies would see an unset
 * {@code mara.tenant_id} and — by design — return nothing at all.
 *
 * <p>Two details are load-bearing:
 *
 * <ul>
 *   <li>{@code set_config(..., true)} makes the setting <b>transaction-scoped</b>. The
 *       service runs behind PgBouncer in transaction pooling mode, which hands the same
 *       physical connection to different requests; a session-scoped setting would
 *       outlive the request and leak into whoever got the connection next.</li>
 *   <li>The value is bound as a <b>parameter</b>, never concatenated. {@code SET} does
 *       not accept parameters, which is exactly why naive implementations build the
 *       statement by hand and open an injection path straight through the isolation
 *       boundary. {@code set_config} is a function, so it takes a bind.</li>
 * </ul>
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final String BIND_TENANT = "SELECT set_config('mara.tenant_id', ?, true)";

    public TenantAwareDataSource(DataSource delegate) {
        super(delegate);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return bind(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return bind(super.getConnection(username, password));
    }

    private Connection bind(Connection connection) throws SQLException {
        String tenantId = TenantContext.current();
        try (PreparedStatement statement = connection.prepareStatement(BIND_TENANT)) {
            // An unset tenant binds the empty string rather than skipping the call.
            // current_tenant() maps that to NULL and every policy matches nothing, so
            // an unscoped request reads no rows instead of all of them.
            statement.setString(1, tenantId == null ? "" : tenantId);
            statement.execute();
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }
}
