package com.hms.platform.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;

/**
 * Stamps every transaction with the caller's organisation, using SET LOCAL semantics
 * ({@code set_config(..., true)}), so the value dies with the transaction and can never leak to the
 * next request on a pooled connection. Postgres row-level security reads it; this is the only place
 * the tenant is ever handed to the database. With no tenant it is blanked, so tenant tables return
 * nothing rather than everything.
 */
public class TenantTransactionManager extends DataSourceTransactionManager {

    public TenantTransactionManager(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        TenantContext.Tenant tenant = TenantContext.orNull();
        Connection connection = DataSourceUtils.getConnection(obtainDataSource());
        try (PreparedStatement ps =
                connection.prepareStatement("SELECT set_config('app.org_id', ?, true), set_config('app.practitioner_id', ?, true)")) {
            ps.setString(1, tenant == null ? "" : tenant.orgId().toString());
            ps.setString(2, tenant == null || tenant.practitionerId() == null ? "" : tenant.practitionerId().toString());
            ps.execute();
        } catch (SQLException e) {
            throw new TransactionSystemException("could not scope the transaction to its organisation", e);
        } catch (CannotGetJdbcConnectionException e) {
            throw new TransactionSystemException("no connection to scope", e);
        }
    }
}
