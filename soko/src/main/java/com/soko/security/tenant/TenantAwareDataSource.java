package com.soko.security.tenant;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Hands the database the tenant (or the system key) for the transaction about to run, so the
 * row-level security policies in {@code V7__row_level_security.sql} can enforce it.
 *
 * <p>The binding happens when {@code setAutoCommit(false)} is called, which is the first moment
 * of a real transaction: binding at checkout would land in a one-statement autocommit
 * transaction that is over before the caller's queries run, and row-level security does not
 * error on that, it silently returns no rows. {@code set_config(..., true)} scopes the setting
 * to the transaction, so it vanishes at commit and cannot leak to whoever gets the pooled
 * connection next. Values are bound as parameters, never concatenated.
 *
 * <p>Consequence: tenant scoping exists only inside a transaction. A query run under plain
 * autocommit sees no tenant and therefore no rows, which fails closed.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final String BIND =
            "SELECT set_config('soko.tenant_id', ?, true), set_config('soko.system_key', ?, true)";

    private final String systemKey;

    public TenantAwareDataSource(DataSource delegate, String systemKey) {
        super(delegate);
        this.systemKey = systemKey;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(super.getConnection(username, password));
    }

    private Connection wrap(Connection raw) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, new Handler(raw, systemKey));
    }

    private static final class Handler implements InvocationHandler {
        private final Connection delegate;
        private final String systemKey;

        Handler(Connection delegate, String systemKey) {
            this.delegate = delegate;
            this.systemKey = systemKey;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                Object result = method.invoke(delegate, args);
                if ("setAutoCommit".equals(method.getName()) && args != null && args.length == 1
                        && Boolean.FALSE.equals(args[0])) {
                    bind();
                }
                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private void bind() throws SQLException {
            try (PreparedStatement statement = delegate.prepareStatement(BIND)) {
                statement.setString(1, TenantBinding.tenant());
                statement.setString(2, TenantBinding.system() ? systemKey : "");
                statement.execute();
            }
        }
    }
}
