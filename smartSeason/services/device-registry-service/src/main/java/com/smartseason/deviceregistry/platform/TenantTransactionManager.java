package com.smartseason.deviceregistry.platform;

import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Configuration
public class TenantTransactionManager {

    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory,
            @Value("${smartseason.tenancy.rls:true}") boolean rls) {
        return new Binding(entityManagerFactory, rls);
    }

    static final class Binding extends JpaTransactionManager {

        private final boolean rls;

        Binding(EntityManagerFactory entityManagerFactory, boolean rls) {
            super(entityManagerFactory);
            this.rls = rls;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            super.doBegin(transaction, definition);
            if (!rls) {
                return;
            }
            UUID tenantId = TenantContext.tenantId().orElse(null);
            if (tenantId == null) {
                return;
            }
            EntityManagerHolder holder = (EntityManagerHolder)
                    TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
            if (holder != null) {
                TenantSession.apply(holder.getEntityManager(), tenantId);
            }
        }
    }
}
