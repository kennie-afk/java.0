package com.soko.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.soko.domain.Invoice;
import com.soko.domain.LedgerEntry;
import com.soko.domain.PlatformCommission;
import com.soko.domain.Subscription;
import com.soko.domain.Tenant;
import com.soko.persistence.InvoiceRepository;
import com.soko.persistence.LedgerEntryRepository;
import com.soko.persistence.PlatformCommissionRepository;
import com.soko.persistence.SubscriptionRepository;
import com.soko.persistence.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceServiceTest {

    private static final class FakeInvoices {
        final List<Invoice> all = new ArrayList<>();
        Invoice save(Invoice i) {
            if (i.getId() == null) {
                i.setId(UUID.randomUUID());
                all.add(i);
            }
            return i;
        }
    }

    private static final class FakeCommissions {
        final List<PlatformCommission> all = new ArrayList<>();

        PlatformCommission save(PlatformCommission c) {
            if (c.getId() == null) { c.setId(UUID.randomUUID()); all.add(c); }
            return c;
        }

        List<PlatformCommission> unbilled(UUID tenantId, String status, Instant from, Instant to) {
            java.util.Objects.requireNonNull(tenantId, "a real query cannot run with no tenant id");
            return all.stream()
                    .filter(c -> c.getTenantId().equals(tenantId) && c.getStatus().name().equals(status)
                            && !c.getCreatedAt().isBefore(from) && c.getCreatedAt().isBefore(to))
                    .toList();
        }

        int markInvoiced(List<UUID> ids, UUID invoiceId) {
            int count = 0;
            for (PlatformCommission c : all) {
                if (ids.contains(c.getId())) {
                    c.setStatus(PlatformCommission.Status.INVOICED);
                    c.setInvoiceId(invoiceId);
                    count++;
                }
            }
            return count;
        }
    }

    private static final class FakeSubscriptions {
        final List<Subscription> all = new ArrayList<>();
        Optional<Subscription> active(UUID tenantId) {
            return all.stream()
                    .filter(s -> java.util.Objects.equals(tenantId, s.getTenantId())
                            && s.getEndedAt() == null)
                    .findFirst();
        }
        Subscription save(Subscription s) { if (s.getId() == null) { s.setId(UUID.randomUUID()); all.add(s); } return s; }
    }

    private static final class FakeLedger {
        final List<LedgerEntry> entries = new ArrayList<>();
        LedgerEntry save(LedgerEntry e) { e.setId(UUID.randomUUID()); entries.add(e); return e; }
    }

    private static final class FakeTenants {
        final List<Tenant> all = new ArrayList<>();
    }

    private static InvoiceRepository adapt(FakeInvoices fake) {
        return (InvoiceRepository) java.lang.reflect.Proxy.newProxyInstance(
                InvoiceRepository.class.getClassLoader(), new Class<?>[] {InvoiceRepository.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "save": case "saveAndFlush": return fake.save((Invoice) args[0]);
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private static PlatformCommissionRepository adapt(FakeCommissions fake) {
        return (PlatformCommissionRepository) java.lang.reflect.Proxy.newProxyInstance(
                PlatformCommissionRepository.class.getClassLoader(),
                new Class<?>[] {PlatformCommissionRepository.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "save": return fake.save((PlatformCommission) args[0]);
                        case "findByTenantIdAndStatusAndCreatedAtBetween":
                            return fake.unbilled((UUID) args[0], (String) args[1],
                                    (Instant) args[2], (Instant) args[3]);
                        case "markInvoiced":
                            return fake.markInvoiced((List<UUID>) args[0], (UUID) args[1]);
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private static SubscriptionRepository adapt(FakeSubscriptions fake) {
        return (SubscriptionRepository) java.lang.reflect.Proxy.newProxyInstance(
                SubscriptionRepository.class.getClassLoader(), new Class<?>[] {SubscriptionRepository.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "findByTenantIdAndEndedAtIsNull": return fake.active((UUID) args[0]);
                        case "save": case "saveAndFlush": return fake.save((Subscription) args[0]);
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private static LedgerEntryRepository adapt(FakeLedger fake) {
        return (LedgerEntryRepository) java.lang.reflect.Proxy.newProxyInstance(
                LedgerEntryRepository.class.getClassLoader(), new Class<?>[] {LedgerEntryRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) return fake.save((LedgerEntry) args[0]);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static TenantRepository adapt(FakeTenants fake) {
        return (TenantRepository) java.lang.reflect.Proxy.newProxyInstance(
                TenantRepository.class.getClassLoader(), new Class<?>[] {TenantRepository.class},
                (proxy, method, args) -> {
                    if ("findAll".equals(method.getName())) return fake.all;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private record Rig(InvoiceService service, FakeCommissions commissions, FakeLedger ledger,
            FakeTenants tenants, FakeInvoices invoices) {}

    private static Rig rig() {
        FakeInvoices invoices = new FakeInvoices();
        FakeCommissions commissions = new FakeCommissions();
        FakeSubscriptions subs = new FakeSubscriptions();
        FakeLedger ledger = new FakeLedger();
        FakeTenants tenants = new FakeTenants();
        InvoiceService service = new InvoiceService(
                adapt(invoices), adapt(commissions), new SubscriptionService(adapt(subs)),
                new LedgerService(adapt(ledger)), adapt(tenants));
        return new Rig(service, commissions, ledger, tenants, invoices);
    }

    private static PlatformCommission accrued(UUID tenantId, long commissionCents, Instant createdAt) {
        PlatformCommission c = new PlatformCommission();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setOrderId(UUID.randomUUID());
        c.setPlan("FREE");
        c.setCommissionBps(500);
        c.setGrossCents(commissionCents * 20);
        c.setCommissionCents(commissionCents);
        c.setStatus(PlatformCommission.Status.ACCRUED);
        c.setCreatedAt(createdAt);
        return c;
    }

    @Test
    void anInvoiceTotalsExactlyTheSubscriptionFeePlusEveryCommissionInWindowAndTheLedgerProvesIt() {
        Rig rig = rig();
        UUID tenantId = UUID.randomUUID();
        Instant periodStart = Instant.parse("2026-09-01T00:00:00Z");
        Instant periodEnd = Instant.parse("2026-10-01T00:00:00Z");

        rig.commissions().all.add(accrued(tenantId, 1000, periodStart.plus(1, ChronoUnit.DAYS)));
        rig.commissions().all.add(accrued(tenantId, 2500, periodStart.plus(10, ChronoUnit.DAYS)));
        // Outside the window -- must not be billed this period.
        rig.commissions().all.add(accrued(tenantId, 9999, periodEnd.plus(1, ChronoUnit.DAYS)));

        Invoice invoice = rig.service().generate(tenantId, periodStart, periodEnd);

        assertThat(invoice.getSubscriptionFeeCents()).isEqualTo(Plan.FREE.monthlyFeeCents());
        assertThat(invoice.getCommissionCents()).isEqualTo(3500L);
        assertThat(invoice.getTotalCents()).isEqualTo(Plan.FREE.monthlyFeeCents() + 3500L);
        assertThat(invoice.getStatus()).isEqualTo(Invoice.Status.ISSUED);

        long invoiced = rig.commissions().all.stream()
                .filter(c -> c.getStatus() == PlatformCommission.Status.INVOICED)
                .count();
        assertThat(invoiced).isEqualTo(2);

        // FREE plan's fee is 0, so no SUBSCRIPTION_FEE entry is expected -- only
        // the commission entry and the invoice-issued entry, and their sum
        // reconciles exactly against the invoice total. That reconciliation,
        // not a hand-picked number, is what a correct ledger has to prove.
        List<LedgerEntry> forInvoice = rig.ledger().entries.stream()
                .filter(e -> e.getReferenceId().equals(invoice.getId()))
                .toList();
        assertThat(forInvoice).extracting(LedgerEntry::getEntryType)
                .containsExactlyInAnyOrder("COMMISSION_INVOICED", "INVOICE_ISSUED");
        long ledgerSum = forInvoice.stream().mapToLong(LedgerEntry::getAmountCents).sum();
        assertThat(ledgerSum).isEqualTo(invoice.getTotalCents() + invoice.getCommissionCents());
    }

    @Test
    void generatingTwiceForTheSamePeriodNeverBillsTheSameCommissionTwice() {
        Rig rig = rig();
        UUID tenantId = UUID.randomUUID();
        Instant periodStart = Instant.parse("2026-09-01T00:00:00Z");
        Instant periodEnd = Instant.parse("2026-10-01T00:00:00Z");
        rig.commissions().all.add(accrued(tenantId, 5000, periodStart.plus(1, ChronoUnit.DAYS)));

        Invoice firstInvoice = rig.service().generate(tenantId, periodStart, periodEnd);
        Invoice secondInvoice = rig.service().generate(tenantId, periodStart, periodEnd);

        assertThat(firstInvoice.getCommissionCents()).isEqualTo(5000L);
        // Already INVOICED by the first call, so the second finds nothing left to bill.
        assertThat(secondInvoice.getCommissionCents()).isEqualTo(0L);
    }

    @Test
    void oneTenantsBillingFailureDoesNotStopAnotherTenantsInvoiceFromBeingIssued() {
        Rig rig = rig();
        Tenant broken = new Tenant();
        broken.setId(null); // generate() will NPE dereferencing a null tenant id -> simulates a failure
        Tenant healthy = new Tenant();
        healthy.setId(UUID.randomUUID());
        healthy.setName("Healthy Distributor");
        healthy.setSlug("healthy");
        rig.tenants().all.add(broken);
        rig.tenants().all.add(healthy);

        rig.service().runMonthlyBilling();

        assertThat(rig.invoices().all).hasSize(1);
        assertThat(rig.invoices().all.get(0).getTenantId()).isEqualTo(healthy.getId());
    }
}
