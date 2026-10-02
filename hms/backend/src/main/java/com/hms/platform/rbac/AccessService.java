package com.hms.platform.rbac;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.platform.config.HmsProperties;
import com.hms.platform.tenancy.TenantContext;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What a signed-in person may do and where, read from the organisation's own role rows. Permissions
 * are NOT carried in the token: an edit to a role, a removed assignment or a disabled account takes
 * effect on the next request once the short cache expires (HMS_ROLE_CACHE_TTL_MS, default 15 s).
 * A change made on this server clears its cache immediately.
 */
@Service
public class AccessService {

    public record Access(boolean active, Set<String> permissions, Set<UUID> facilityIds) {}

    private record Cached(Access access, long loadedAt) {}

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final long ttlMs;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public AccessService(JdbcClient jdbc, PlatformTransactionManager txm, ObjectMapper json, HmsProperties props) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.tx.setReadOnly(true);
        this.json = json;
        this.ttlMs = props.roles().cacheTtlMs();
    }

    public void invalidateAll() {
        cache.clear();
    }

    /** Must be called with the caller's organisation already in TenantContext (so RLS applies). */
    public Access of(UUID orgId, UUID practitionerId) {
        Cached hit = cache.get(practitionerId);
        if (hit != null && System.currentTimeMillis() - hit.loadedAt() < ttlMs) {
            return hit.access();
        }
        Access fresh = tx.execute(status -> load(practitionerId));
        cache.put(practitionerId, new Cached(fresh, System.currentTimeMillis()));
        return fresh;
    }

    private Access load(UUID practitionerId) {
        String state = jdbc.sql("SELECT status FROM practitioners WHERE id = ?").param(practitionerId)
                .query(String.class).optional().orElse(null);
        if (!"ACTIVE".equals(state)) {
            return new Access(false, Set.of(), Set.of());
        }
        Set<String> permissions = new HashSet<>();
        var roles = jdbc.sql("""
                SELECT r.role_key, r.permissions::text AS permissions
                  FROM practitioner_roles pr
                  JOIN roles r ON r.org_id = pr.org_id AND r.role_key = pr.role_key
                 WHERE pr.practitioner_id = ?""").param(practitionerId)
                .query((rs, n) -> Map.entry(rs.getString("role_key"), rs.getString("permissions"))).list();
        for (var role : roles) {
            if (DefaultRoles.ADMIN.equals(role.getKey())) {
                // The administrator role is always everything, whatever its stored row says.
                permissions.addAll(Permissions.ALL);
                continue;
            }
            try {
                for (String permission : json.readValue(role.getValue(), STRINGS)) {
                    if (Permissions.ALL.contains(permission)) {
                        permissions.add(permission);
                    }
                }
            } catch (Exception e) {
                // A role row we cannot read grants nothing: least privilege.
            }
        }
        Set<UUID> facilities = new HashSet<>(jdbc.sql("SELECT facility_id FROM practitioner_facilities WHERE practitioner_id = ?")
                .param(practitionerId).query(UUID.class).list());
        return new Access(true, Set.copyOf(permissions), Set.copyOf(facilities));
    }

    /** Resolves access for a person we have only just authenticated, inside their tenant scope. */
    public Access resolve(UUID orgId, UUID practitionerId) {
        TenantContext.Tenant previous = TenantContext.orNull();
        TenantContext.set(new TenantContext.Tenant(orgId, practitionerId, Set.of(), Set.of()));
        try {
            return of(orgId, practitionerId);
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }
}
