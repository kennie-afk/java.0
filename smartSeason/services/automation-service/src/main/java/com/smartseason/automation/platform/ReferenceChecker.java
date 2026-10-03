package com.smartseason.automation.platform;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ReferenceChecker {

    private static final Pattern ENTITY = Pattern.compile("[A-Za-z][A-Za-z0-9]*");

    @PersistenceContext
    private EntityManager entityManager;

    private final boolean enabled;

    public ReferenceChecker() {
        this(true);
    }

    private ReferenceChecker(boolean enabled) {
        this.enabled = enabled;
    }

    public static ReferenceChecker disabled() {
        return new ReferenceChecker(false);
    }

    public void require(String entity, String field, UUID id) {
        if (!enabled || id == null) {
            return;
        }
        if (!ENTITY.matcher(entity).matches()) {
            throw new IllegalArgumentException("Not an entity name: " + entity);
        }
        Long found = entityManager
                .createQuery("select count(e) from " + entity + " e where e.id = :id and e.tenantId = :tenant",
                        Long.class)
                .setParameter("id", id)
                .setParameter("tenant", TenantContext.requireTenantId())
                .getSingleResult();
        if (found == null || found == 0L) {
            throw new DomainRuleException(field + " does not refer to a " + entity + " in your organisation");
        }
    }
}
