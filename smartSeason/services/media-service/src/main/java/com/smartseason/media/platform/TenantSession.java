package com.smartseason.media.platform;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TenantSession {

    @PersistenceContext
    private EntityManager entityManager;

    private final boolean rls;

    public TenantSession(@Value("${smartseason.tenancy.rls:true}") boolean rls) {
        this.rls = rls;
    }

    public void bind(UUID tenantId) {
        TenantContext.set(tenantId);
        if (rls && TransactionSynchronizationManager.isActualTransactionActive()) {
            apply(entityManager, tenantId);
        }
    }

    static void apply(EntityManager em, UUID tenantId) {
        em.createNativeQuery("select set_config('app.tenant_id', :tenant, true)")
                .setParameter("tenant", tenantId == null ? "" : tenantId.toString())
                .getSingleResult();
    }
}
