package com.soko.billing;

import com.soko.domain.Subscription;
import com.soko.persistence.SubscriptionRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A tenant with no {@link Subscription} row at all (every tenant created
 * before this feature existed, and every one created since without an
 * explicit choice) is FREE -- {@link #current} creates that row lazily
 * rather than needing a backfill migration to touch every existing tenant.
 */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;

    public SubscriptionService(SubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Transactional
    public Subscription current(UUID tenantId) {
        return subscriptions.findByTenantIdAndEndedAtIsNull(tenantId)
                .orElseGet(() -> {
                    try {
                        return startNew(tenantId, Plan.FREE);
                    } catch (DataIntegrityViolationException raceLost) {
                        // Another concurrent first request already created the FREE
                        // row and won the partial-unique-index race; use theirs.
                        return subscriptions.findByTenantIdAndEndedAtIsNull(tenantId)
                                .orElseThrow(() -> raceLost);
                    }
                });
    }

    @Transactional
    public Subscription changePlan(UUID tenantId, Plan plan) {
        subscriptions.findByTenantIdAndEndedAtIsNull(tenantId).ifPresent(existing -> {
            existing.setEndedAt(Instant.now());
            // Hibernate's flush order is inserts-then-updates within a single
            // flush, regardless of call order -- so without an explicit flush
            // here, the new row's INSERT below would land before this row's
            // ended_at UPDATE, and the partial unique index would (correctly)
            // see two active rows for an instant and reject it.
            subscriptions.saveAndFlush(existing);
        });
        return startNew(tenantId, plan);
    }

    private Subscription startNew(UUID tenantId, Plan plan) {
        Subscription subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setPlan(plan.name());
        subscription.setMonthlyFeeCents(plan.monthlyFeeCents());
        subscription.setCommissionBps(plan.commissionBps());
        // Flushed immediately (not left for commit) so the partial unique
        // index's violation, if another request just won this race, surfaces
        // right here where current() can catch it -- not later, unhandled,
        // at transaction commit.
        return subscriptions.saveAndFlush(subscription);
    }
}
