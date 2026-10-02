package com.mara.kit.tenant;

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
 * Wraps the pooled {@code DataSource} so a connection carries the current tenant in the
 * session variable that row-level security reads, for the whole transaction the
 * application runs its queries in.
 *
 * <p>This is the join between {@link TenantContext} and the policies in
 * {@code V2__row_level_security.sql}. Without it the policies see an unset
 * {@code mara.tenant_id} and — by design — return nothing at all.
 *
 * <p><b>The binding happens when the transaction actually begins, not at connection
 * checkout.</b> {@code set_config(..., true)} scopes the setting to "the current
 * transaction" — but at raw checkout, under the default autocommit mode every pooled
 * connection starts in, there isn't a transaction yet in the sense that matters: a
 * statement sent while autocommit is still {@code true} opens and closes its own
 * one-statement transaction, and Spring's transaction managers only call
 * {@code setAutoCommit(false)} <em>after</em> {@code getConnection()} returns. Binding at
 * checkout therefore sets the tenant for a transaction that is already over before the
 * caller's own queries run in a different one — and row-level security does not error on
 * that, it just returns no rows, so the bug is silent. This class instead intercepts
 * {@link Connection#setAutoCommit} and binds the instant it is called with {@code false}:
 * that is the actual first moment of the transaction the caller's statements will run in,
 * so the binding lands in the same transaction that needs it.
 *
 * <p>One consequence follows directly: tenant scoping only exists inside an explicit
 * transaction. Code that queries tenant-scoped tables under plain autocommit — no
 * {@code @Transactional}, no {@code TransactionTemplate} — gets no binding at all, because
 * there is no transaction for a transaction-scoped setting to attach to. Every current
 * caller already runs through one; any new one must too.
 *
 * <p>Two further details are load-bearing:
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
        return wrap(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(super.getConnection(username, password));
    }

    private static Connection wrap(Connection raw) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                new TenantBindingHandler(raw));
    }

    /**
     * Forwards every call to the real connection, and binds the tenant right after
     * {@code setAutoCommit(false)} — the moment a real transaction starts.
     */
    private static final class TenantBindingHandler implements InvocationHandler {
        private final Connection delegate;

        TenantBindingHandler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                Object result = method.invoke(delegate, args);
                if (isBeginningATransaction(method, args)) {
                    bind();
                }
                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private static boolean isBeginningATransaction(Method method, Object[] args) {
            return "setAutoCommit".equals(method.getName())
                    && args != null
                    && args.length == 1
                    && Boolean.FALSE.equals(args[0]);
        }

        private void bind() throws SQLException {
            String tenantId = TenantContext.current();
            try (PreparedStatement statement = delegate.prepareStatement(BIND_TENANT)) {
                // An unset tenant binds the empty string rather than skipping the call.
                // current_tenant() maps that to NULL and every policy matches nothing,
                // so an unscoped request reads no rows instead of all of them.
                statement.setString(1, tenantId == null ? "" : tenantId);
                statement.execute();
            }
        }
    }
}
