package com.smartseason.identity.platform;

import com.smartseason.identity.repo.RefreshTokenRepository;
import com.smartseason.identity.repo.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Finds which tenant a credential belongs to, before any tenant is known.
 *
 * <p>Sign-in is by e-mail address and refresh is by token, so there is no tenant in hand when
 * the first query runs - and with row-level security an unbound query sees no rows at all.
 * These lookups go through two narrow database functions (V4__tenant_lookup.sql) that return
 * a tenant id and nothing else, never a row. The caller then binds that tenant with
 * {@link TenantSession#bind} and continues with ordinary, fully filtered queries.
 *
 * <p>Without row-level security (the H2 unit tests) there is nothing to bypass, so the same
 * answer comes from the repositories.
 */
@Component
public class IdentityTenantLookup {

    @PersistenceContext
    private EntityManager entityManager;

    private final boolean rls;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;

    public IdentityTenantLookup(@Value("${smartseason.tenancy.rls:true}") boolean rls,
                                UserRepository users,
                                RefreshTokenRepository refreshTokens) {
        this.rls = rls;
        this.users = users;
        this.refreshTokens = refreshTokens;
    }

    public Optional<UUID> byEmail(String normalisedEmail) {
        if (normalisedEmail == null) {
            return Optional.empty();
        }
        if (!rls) {
            return users.findByEmail(normalisedEmail).map(u -> u.getTenantId());
        }
        return first("select ss_identity_tenant_by_email(:value)", normalisedEmail);
    }

    public Optional<UUID> byRefreshTokenHash(String tokenHash) {
        if (tokenHash == null) {
            return Optional.empty();
        }
        if (!rls) {
            return refreshTokens.findByTokenHash(tokenHash).map(t -> t.getTenantId());
        }
        return first("select ss_identity_tenant_by_refresh_hash(:value)", tokenHash);
    }

    public boolean emailTaken(String normalisedEmail) {
        return byEmail(normalisedEmail).isPresent();
    }

    private Optional<UUID> first(String sql, String value) {
        List<?> rows = entityManager.createNativeQuery(sql).setParameter("value", value).getResultList();
        if (rows.isEmpty() || rows.get(0) == null) {
            return Optional.empty();
        }
        Object id = rows.get(0);
        return Optional.of(id instanceof UUID uuid ? uuid : UUID.fromString(id.toString()));
    }
}
