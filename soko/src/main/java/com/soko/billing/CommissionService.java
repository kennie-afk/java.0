package com.soko.billing;

import com.soko.domain.PlatformCommission;
import com.soko.domain.Subscription;
import com.soko.persistence.PlatformCommissionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The fee Soko-the-platform earns on each order it routes -- separate from,
 * and never visible alongside, the distributor's own margin on that order
 * (see {@code SalesOrder.marginCents} and the customer/supplier payload
 * boundary {@code tools/roles_check.py} enforces).
 */
@Service
public class CommissionService {

    private final PlatformCommissionRepository commissions;
    private final SubscriptionService subscriptions;
    private final LedgerService ledger;

    public CommissionService(PlatformCommissionRepository commissions,
            SubscriptionService subscriptions, LedgerService ledger) {
        this.commissions = commissions;
        this.subscriptions = subscriptions;
        this.ledger = ledger;
    }

    @Transactional
    public PlatformCommission accrue(UUID tenantId, UUID orderId, long revenueCents) {
        Subscription active = subscriptions.current(tenantId);

        PlatformCommission commission = new PlatformCommission();
        commission.setTenantId(tenantId);
        commission.setOrderId(orderId);
        commission.setPlan(active.getPlan());
        commission.setCommissionBps(active.getCommissionBps());
        commission.setGrossCents(revenueCents);
        commission.setCommissionCents(
                Math.floorDiv(revenueCents * active.getCommissionBps(), 10_000L));
        commission.setStatus(PlatformCommission.Status.ACCRUED);
        commission = commissions.save(commission);

        ledger.append(tenantId, "COMMISSION", "ORDER", orderId,
                commission.getCommissionCents(),
                active.getPlan() + " plan, " + active.getCommissionBps() + " bps");
        return commission;
    }

    /** An order cancelled before payment owes Soko nothing for it after all. */
    @Transactional
    public void voidForOrder(UUID orderId) {
        commissions.findByOrderId(orderId).ifPresent(commission -> {
            if (commission.getStatus() != PlatformCommission.Status.ACCRUED) {
                // Already invoiced (or already voided) -- an invoiced commission
                // has already left an immutable mark on an issued invoice and is
                // not retroactively erased by a cancellation that came too late.
                return;
            }
            commission.setStatus(PlatformCommission.Status.VOIDED);
            commissions.save(commission);
            ledger.append(commission.getTenantId(), "COMMISSION_VOIDED", "ORDER", orderId,
                    -commission.getCommissionCents(), "order cancelled before invoicing");
        });
    }
}
