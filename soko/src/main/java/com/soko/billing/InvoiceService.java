package com.soko.billing;

import com.soko.domain.Invoice;
import com.soko.domain.PlatformCommission;
import com.soko.domain.Subscription;
import com.soko.domain.Tenant;
import com.soko.persistence.InvoiceRepository;
import com.soko.persistence.PlatformCommissionRepository;
import com.soko.persistence.TenantRepository;
import com.soko.platform.Errors;
import com.soko.security.tenant.TenantBinding;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bills a tenant for one period: their subscription fee plus every
 * commission accrued in that window. Callable on demand ({@code
 * BillingController}) and run automatically once a month for every tenant
 * (see {@link #runMonthlyBilling()}) -- the same method backs both, so a
 * manual invoice and the automatic one are never two different code paths
 * that could quietly drift apart.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final Duration PAYMENT_TERM = Duration.ofDays(14);

    private final InvoiceRepository invoices;
    private final PlatformCommissionRepository commissions;
    private final SubscriptionService subscriptions;
    private final LedgerService ledger;
    private final TenantRepository tenants;

    private TransactionTemplate perTenant;

    /** One transaction per tenant for the monthly run, so one tenant's failure cannot undo another's invoice. */
    @Autowired(required = false)
    public void setTransactionManager(PlatformTransactionManager manager) {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.perTenant = template;
    }

    public InvoiceService(InvoiceRepository invoices, PlatformCommissionRepository commissions,
            SubscriptionService subscriptions, LedgerService ledger, TenantRepository tenants) {
        this.invoices = invoices;
        this.commissions = commissions;
        this.subscriptions = subscriptions;
        this.ledger = ledger;
        this.tenants = tenants;
    }

    @Transactional
    public Invoice generate(UUID tenantId, Instant periodStart, Instant periodEnd) {
        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("a billing period must have a positive length");
        }

        // Invoicing a period twice would bill the subscription fee twice. The lock makes the check
        // safe against a second replica running at the same moment; the check makes a rerun a no-op.
        String key = tenantId + "|" + periodStart + "|" + periodEnd;
        if (!invoices.tryLockPeriod(key)
                || invoices.existsByTenantIdAndPeriodStartAndPeriodEnd(tenantId, periodStart, periodEnd)) {
            throw new Errors.BadRequest("that billing period has already been invoiced");
        }

        Subscription active = subscriptions.current(tenantId);
        List<PlatformCommission> unbilled = commissions.findByTenantIdAndStatusAndCreatedAtBetween(
                tenantId, PlatformCommission.Status.ACCRUED.name(), periodStart, periodEnd);
        long commissionCents = unbilled.stream().mapToLong(PlatformCommission::getCommissionCents).sum();
        long total = active.getMonthlyFeeCents() + commissionCents;

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setReference("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        invoice.setPeriodStart(periodStart);
        invoice.setPeriodEnd(periodEnd);
        invoice.setSubscriptionFeeCents(active.getMonthlyFeeCents());
        invoice.setCommissionCents(commissionCents);
        invoice.setTotalCents(total);
        invoice.setStatus(Invoice.Status.ISSUED);
        invoice.setDueAt(Instant.now().plus(PAYMENT_TERM));
        // A bulk @Modifying query runs immediately against the database,
        // bypassing the entity manager's deferred write-behind cache -- so
        // without flushing first, this invoice row would not exist yet when
        // the commissions below try to reference its id, and the foreign key
        // would (correctly) reject them.
        invoice = invoices.saveAndFlush(invoice);

        if (!unbilled.isEmpty()) {
            commissions.markInvoiced(unbilled.stream().map(PlatformCommission::getId).toList(),
                    invoice.getId());
        }

        if (active.getMonthlyFeeCents() > 0) {
            ledger.append(tenantId, "SUBSCRIPTION_FEE", "INVOICE", invoice.getId(),
                    active.getMonthlyFeeCents(), active.getPlan() + " plan, monthly fee");
        }
        if (commissionCents > 0) {
            ledger.append(tenantId, "COMMISSION_INVOICED", "INVOICE", invoice.getId(),
                    commissionCents, unbilled.size() + " order(s) commission");
        }
        ledger.append(tenantId, "INVOICE_ISSUED", "INVOICE", invoice.getId(), total,
                invoice.getReference());

        return invoice;
    }

    /**
     * Every tenant, once a month, for the calendar month that just ended.
     * 03:00 on the 1st -- deliberately off-peak and after midnight rollover
     * has definitely settled on every relevant clock.
     */
    @Scheduled(cron = "${soko.billing.cron:0 0 3 1 * *}")
    public void runMonthlyBilling() {
        ZonedDateTime firstOfThisMonth =
                ZonedDateTime.now(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate()
                        .atStartOfDay(ZoneOffset.UTC);
        Instant periodEnd = firstOfThisMonth.toInstant();
        Instant periodStart = firstOfThisMonth.minusMonths(1).toInstant();

        // Listing every tenant is the one cross-tenant read; each tenant is then billed in its own
        // transaction bound to that tenant alone.
        List<UUID> tenantIds = TenantBinding.asSystem(() -> tenants.findAll()).stream().map(Tenant::getId).toList();
        for (UUID tenantId : tenantIds) {
            try {
                Invoice invoice = TenantBinding.runAs(tenantId,
                        () -> perTenant.execute(status -> generate(tenantId, periodStart, periodEnd)));
                log.info("issued invoice {} for tenant {}: {} cents",
                        invoice.getReference(), tenantId, invoice.getTotalCents());
            } catch (Errors.BadRequest already) {
                log.info("tenant {} is already invoiced for {}: {}", tenantId, periodStart, already.getMessage());
            } catch (RuntimeException ex) {
                // One tenant's billing failure must never block every other tenant's invoice for
                // the month: each has its own transaction, so this one rolls back alone.
                log.error("could not bill tenant {}: {}", tenantId, ex.getMessage());
            }
        }
    }
}
