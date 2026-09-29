package com.soko.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.soko.domain.LedgerEntry;
import com.soko.domain.PlatformCommission;
import com.soko.domain.Subscription;
import com.soko.persistence.LedgerEntryRepository;
import com.soko.persistence.PlatformCommissionRepository;
import com.soko.persistence.SubscriptionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommissionServiceTest {

    private static final class FakeCommissions {
        private final List<PlatformCommission> all = new ArrayList<>();

        PlatformCommission save(PlatformCommission c) {
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
                all.add(c);
            }
            return c;
        }

        Optional<PlatformCommission> byOrder(UUID orderId) {
            return all.stream().filter(c -> c.getOrderId().equals(orderId)).findFirst();
        }
    }

    private static final class FakeSubscriptions {
        private final List<Subscription> all = new ArrayList<>();

        Optional<Subscription> active(UUID tenantId) {
            return all.stream().filter(s -> s.getTenantId().equals(tenantId) && s.getEndedAt() == null).findFirst();
        }

        Subscription save(Subscription s) {
            if (s.getId() == null) { s.setId(UUID.randomUUID()); all.add(s); }
            return s;
        }
    }

    private static final class FakeLedger {
        final List<LedgerEntry> entries = new ArrayList<>();
        LedgerEntry save(LedgerEntry e) { e.setId(UUID.randomUUID()); entries.add(e); return e; }
    }

    private static PlatformCommissionRepository adaptCommissions(FakeCommissions fake) {
        return (PlatformCommissionRepository) java.lang.reflect.Proxy.newProxyInstance(
                PlatformCommissionRepository.class.getClassLoader(),
                new Class<?>[] {PlatformCommissionRepository.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "save": return fake.save((PlatformCommission) args[0]);
                        case "findByOrderId": return fake.byOrder((UUID) args[0]);
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private static SubscriptionRepository adaptSubscriptions(FakeSubscriptions fake) {
        return (SubscriptionRepository) java.lang.reflect.Proxy.newProxyInstance(
                SubscriptionRepository.class.getClassLoader(),
                new Class<?>[] {SubscriptionRepository.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "findByTenantIdAndEndedAtIsNull": return fake.active((UUID) args[0]);
                        case "save": case "saveAndFlush": return fake.save((Subscription) args[0]);
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private static LedgerEntryRepository adaptLedger(FakeLedger fake) {
        return (LedgerEntryRepository) java.lang.reflect.Proxy.newProxyInstance(
                LedgerEntryRepository.class.getClassLoader(),
                new Class<?>[] {LedgerEntryRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) return fake.save((LedgerEntry) args[0]);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void accruingOnAFreePlanOrderChargesFiveHundredBasisPoints() {
        FakeCommissions commissions = new FakeCommissions();
        FakeLedger ledger = new FakeLedger();
        CommissionService service = new CommissionService(
                adaptCommissions(commissions),
                new SubscriptionService(adaptSubscriptions(new FakeSubscriptions())),
                new LedgerService(adaptLedger(ledger)));

        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        PlatformCommission commission = service.accrue(tenantId, orderId, 150_000L);

        assertThat(commission.getPlan()).isEqualTo("FREE");
        assertThat(commission.getCommissionCents()).isEqualTo(7_500L); // 5.00% of 150000
        assertThat(commission.getStatus()).isEqualTo(PlatformCommission.Status.ACCRUED);
        assertThat(ledger.entries).hasSize(1);
        assertThat(ledger.entries.get(0).getAmountCents()).isEqualTo(7_500L);
    }

    @Test
    void voidingACancelledOrdersAccruedCommissionMarksItVoidedAndOffsetsTheLedger() {
        FakeCommissions commissions = new FakeCommissions();
        FakeLedger ledger = new FakeLedger();
        CommissionService service = new CommissionService(
                adaptCommissions(commissions),
                new SubscriptionService(adaptSubscriptions(new FakeSubscriptions())),
                new LedgerService(adaptLedger(ledger)));

        UUID orderId = UUID.randomUUID();
        service.accrue(UUID.randomUUID(), orderId, 100_000L);

        service.voidForOrder(orderId);

        PlatformCommission after = commissions.byOrder(orderId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PlatformCommission.Status.VOIDED);
        assertThat(ledger.entries).hasSize(2);
        assertThat(ledger.entries.get(1).getAmountCents()).isNegative();
    }

    @Test
    void voidingAnAlreadyInvoicedCommissionDoesNothing() {
        FakeCommissions commissions = new FakeCommissions();
        FakeLedger ledger = new FakeLedger();
        CommissionService service = new CommissionService(
                adaptCommissions(commissions),
                new SubscriptionService(adaptSubscriptions(new FakeSubscriptions())),
                new LedgerService(adaptLedger(ledger)));

        UUID orderId = UUID.randomUUID();
        PlatformCommission commission = service.accrue(UUID.randomUUID(), orderId, 100_000L);
        commission.setStatus(PlatformCommission.Status.INVOICED);

        service.voidForOrder(orderId);

        assertThat(commissions.byOrder(orderId).orElseThrow().getStatus())
                .isEqualTo(PlatformCommission.Status.INVOICED);
        assertThat(ledger.entries).hasSize(1); // only the original accrual, no voiding entry
    }
}
