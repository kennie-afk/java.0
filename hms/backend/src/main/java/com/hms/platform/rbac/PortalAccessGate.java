package com.hms.platform.rbac;

import com.hms.platform.config.HmsProperties;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Whether a patient-portal account may still use the portal. A portal token carries only the account
 * and patient it was issued to, so on its own it would stay valid for its whole lifetime after the
 * facility disables the account. This is the portal's counterpart of {@link AccessService}: the status is read
 * from the database, cached for a few seconds, and cleared at once when this server changes it.
 */
@Service
public class PortalAccessGate {

    private record Cached(boolean active, long loadedAt) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final long ttlMs;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public PortalAccessGate(JdbcClient jdbc, PlatformTransactionManager txm, HmsProperties props) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.tx.setReadOnly(true);
        this.ttlMs = props.roles().cacheTtlMs();
    }

    public void invalidateAll() {
        cache.clear();
    }

    /** Must be called with the account's organisation already in TenantContext, so row-level security applies. */
    public boolean active(UUID accountId) {
        Cached hit = cache.get(accountId);
        if (hit != null && System.currentTimeMillis() - hit.loadedAt() < ttlMs) {
            return hit.active();
        }
        boolean active = Boolean.TRUE.equals(tx.execute(status ->
                "ACTIVE".equals(jdbc.sql("SELECT status FROM portal_accounts WHERE id = ?").param(accountId)
                        .query(String.class).optional().orElse(null))));
        cache.put(accountId, new Cached(active, System.currentTimeMillis()));
        return active;
    }
}
