package com.smartseason.payment.platform;

import com.smartseason.payment.repo.MpesaTransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Finds which tenant an M-Pesa callback belongs to.
 *
 * <p>The callback is unauthenticated - Safaricom, not a signed-in user, calls it - so there is
 * no tenant until the checkout request id it quotes has been matched to a transaction. With
 * row-level security an unbound query sees no rows, so that match goes through a narrow
 * database function (V4__tenant_lookup.sql) that returns a tenant id and nothing else.
 */
@Component
public class PaymentTenantLookup {

    /** Where callbacks nobody can be attributed to are filed; no tenant can ever read them. */
    public static final UUID UNATTRIBUTED = new UUID(0L, 0L);

    @PersistenceContext
    private EntityManager entityManager;

    private final boolean rls;
    private final MpesaTransactionRepository transactions;

    public PaymentTenantLookup(@Value("${smartseason.tenancy.rls:true}") boolean rls,
                               MpesaTransactionRepository transactions) {
        this.rls = rls;
        this.transactions = transactions;
    }

    public Optional<UUID> byCheckoutRequestId(String checkoutRequestId) {
        if (checkoutRequestId == null) {
            return Optional.empty();
        }
        if (!rls) {
            return transactions.findByCheckoutRequestId(checkoutRequestId).map(t -> t.getTenantId());
        }
        List<?> rows = entityManager.createNativeQuery("select ss_payment_tenant_by_checkout(:value)")
                .setParameter("value", checkoutRequestId).getResultList();
        if (rows.isEmpty() || rows.get(0) == null) {
            return Optional.empty();
        }
        Object id = rows.get(0);
        return Optional.of(id instanceof UUID uuid ? uuid : UUID.fromString(id.toString()));
    }
}
