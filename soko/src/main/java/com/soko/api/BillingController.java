package com.soko.api;

import com.soko.billing.InvoiceService;
import com.soko.billing.LedgerService;
import com.soko.billing.Plan;
import com.soko.billing.SubscriptionService;
import com.soko.domain.Invoice;
import com.soko.domain.LedgerEntry;
import com.soko.domain.MpesaPayment;
import com.soko.domain.Subscription;
import com.soko.payment.PaymentService;
import com.soko.persistence.InvoiceRepository;
import com.soko.persistence.LedgerEntryRepository;
import com.soko.platform.Errors;
import com.soko.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * What a distributor tenant owes Soko-the-platform: their subscription, the
 * commission accrued on their own orders, invoices, and the ledger those
 * invoices are provably built from. Distinct on purpose from every order
 * endpoint elsewhere -- a distributor's own customers must never see any of
 * this (see tools/roles_check.py).
 */
@RestController
@RequestMapping("/v1/billing")
public class BillingController {

    private final SubscriptionService subscriptions;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoices;
    private final LedgerEntryRepository ledgerEntries;
    private final PaymentService payments;
    private final TenantContext context;

    public BillingController(SubscriptionService subscriptions, InvoiceService invoiceService,
            InvoiceRepository invoices, LedgerEntryRepository ledgerEntries,
            PaymentService payments, TenantContext context) {
        this.subscriptions = subscriptions;
        this.invoiceService = invoiceService;
        this.invoices = invoices;
        this.ledgerEntries = ledgerEntries;
        this.payments = payments;
        this.context = context;
    }

    private void requireOwner() {
        if (!"OWNER".equals(context.current().role())) {
            throw new Errors.Unauthorized("only an owner can manage billing");
        }
    }

    @GetMapping("/subscription")
    public Map<String, Object> subscription() {
        Subscription active = subscriptions.current(context.current().tenantId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plan", active.getPlan());
        body.put("monthlyFeeCents", active.getMonthlyFeeCents());
        body.put("commissionBps", active.getCommissionBps());
        body.put("startedAt", active.getStartedAt());
        return body;
    }

    public record PlanChange(@NotBlank String plan) {}

    @PutMapping("/subscription/plan")
    public Map<String, Object> changePlan(@Valid @RequestBody PlanChange request) {
        requireOwner();
        Plan plan;
        try {
            plan = Plan.valueOf(request.plan().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new Errors.BadRequest("plan must be one of " + List.of(Plan.values()));
        }
        Subscription updated = subscriptions.changePlan(context.current().tenantId(), plan);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plan", updated.getPlan());
        body.put("monthlyFeeCents", updated.getMonthlyFeeCents());
        body.put("commissionBps", updated.getCommissionBps());
        return body;
    }

    @GetMapping("/invoices")
    public List<Map<String, Object>> listInvoices(@RequestParam(defaultValue = "50") int limit) {
        UUID tenantId = context.current().tenantId();
        return invoices.findByTenantIdOrderByIssuedAtDesc(
                        tenantId, PageRequest.of(0, Math.min(limit, 200)))
                .stream()
                .map(BillingController::invoiceBody)
                .toList();
    }

    public record InvoicePeriod(@NotNull Instant periodStart, @NotNull Instant periodEnd) {}

    @PostMapping("/invoices/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> generateInvoice(@Valid @RequestBody InvoicePeriod request) {
        requireOwner();
        Invoice invoice = invoiceService.generate(
                context.current().tenantId(), request.periodStart(), request.periodEnd());
        return invoiceBody(invoice);
    }

    public record PayRequest(@NotBlank String msisdn) {}

    @PostMapping("/invoices/{id}/pay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> payInvoice(@PathVariable UUID id, @Valid @RequestBody PayRequest request) {
        requireOwner();
        UUID tenantId = context.current().tenantId();
        Invoice invoice = invoices.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new Errors.NotFound("no such invoice"));
        if (invoice.getStatus() != Invoice.Status.ISSUED) {
            throw new Errors.BadRequest("this invoice is " + invoice.getStatus() + ", not payable");
        }

        MpesaPayment payment = payments.initiate(
                tenantId, MpesaPayment.Purpose.INVOICE, invoice.getId(), invoice.getTotalCents(),
                request.msisdn(), invoice.getReference(), "Soko invoice " + invoice.getReference());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", payment.getStatus());
        body.put("checkoutRequestId", payment.getCheckoutRequestId());
        body.put("detail", payment.getResultDesc());
        return body;
    }

    @GetMapping("/ledger")
    public List<Map<String, Object>> ledger(@RequestParam(defaultValue = "100") int limit) {
        return ledgerEntries
                .findByTenantIdOrderByCreatedAtDesc(
                        context.current().tenantId(), PageRequest.of(0, Math.min(limit, 500)))
                .stream()
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("type", e.getEntryType());
                    row.put("referenceType", e.getReferenceType());
                    row.put("referenceId", e.getReferenceId());
                    row.put("amountCents", e.getAmountCents());
                    row.put("description", e.getDescription());
                    row.put("createdAt", e.getCreatedAt());
                    return row;
                })
                .toList();
    }

    private static Map<String, Object> invoiceBody(Invoice invoice) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", invoice.getId());
        body.put("reference", invoice.getReference());
        body.put("periodStart", invoice.getPeriodStart());
        body.put("periodEnd", invoice.getPeriodEnd());
        body.put("subscriptionFeeCents", invoice.getSubscriptionFeeCents());
        body.put("commissionCents", invoice.getCommissionCents());
        body.put("totalCents", invoice.getTotalCents());
        body.put("status", invoice.getStatus());
        body.put("dueAt", invoice.getDueAt());
        body.put("paidAt", invoice.getPaidAt());
        return body;
    }
}
