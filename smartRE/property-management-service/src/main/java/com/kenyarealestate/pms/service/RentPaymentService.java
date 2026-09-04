package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.client.PaymentServiceClient;
import com.kenyarealestate.pms.dto.*;
import com.kenyarealestate.pms.entity.*;
import com.kenyarealestate.pms.exception.ConflictException;
import com.kenyarealestate.pms.exception.ForbiddenException;
import com.kenyarealestate.pms.exception.NotFoundException;
import com.kenyarealestate.pms.kafka.PmsEventPublisher;
import com.kenyarealestate.pms.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class RentPaymentService {

    private final RentInvoiceRepository invoices;
    private final RentPaymentRepository payments;
    private final LeaseRepository leases;
    private final TenantRepository tenants;
    private final UnitRepository units;
    private final PaymentServiceClient paymentClient;
    private final RentInvoiceService invoiceService;
    private final PmsEventPublisher publisher;

    public RentPaymentService(RentInvoiceRepository invoices, RentPaymentRepository payments,
                              LeaseRepository leases, TenantRepository tenants, UnitRepository units,
                              PaymentServiceClient paymentClient, RentInvoiceService invoiceService,
                              PmsEventPublisher publisher) {
        this.invoices = invoices;
        this.payments = payments;
        this.leases = leases;
        this.tenants = tenants;
        this.units = units;
        this.paymentClient = paymentClient;
        this.invoiceService = invoiceService;
        this.publisher = publisher;
    }

    public RentPaymentResponse startStkPush(UUID callerId, String bearerToken, UUID invoiceId, PayInvoiceRequest req) {
        RentInvoice invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> new NotFoundException("Invoice not found"));

        Tenant tenant = tenants.findById(invoice.getTenantId())
                .orElseThrow(() -> new NotFoundException("Tenant not found"));
        if (!callerId.equals(tenant.getUserId())) {
            throw new ForbiddenException(
                    "Only the tenant on this lease can start an M-Pesa prompt. A landlord should record the payment instead.");
        }
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.WRITTEN_OFF) {
            throw new ConflictException("This invoice is already settled.");
        }

        BigDecimal amount = req.getAmount() != null ? req.getAmount() : invoice.getBalance();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConflictException("There is nothing left to pay on this invoice.");
        }
        if (amount.compareTo(invoice.getBalance()) > 0) {
            throw new ConflictException("That is more than the " + invoice.getBalance() + " still outstanding.");
        }

        String phone = StringUtils.hasText(req.getPhoneNumber()) ? req.getPhoneNumber() : tenant.getPhone();
        Unit unit = units.findById(invoice.getUnitId())
                .orElseThrow(() -> new NotFoundException("Unit not found"));

        InitiatedPaymentResponse initiated = paymentClient.initiateRentStkPush(
                bearerToken, unit.getPropertyId(), invoice.getLandlordId(), amount, phone, invoice.getId());

        if (initiated == null || initiated.getId() == null) {
            throw new ConflictException("Could not start the M-Pesa prompt. Try again in a moment.");
        }

        RentPayment record = RentPayment.builder()
                .invoiceId(invoice.getId())
                .leaseId(invoice.getLeaseId())
                .paymentId(initiated.getId())
                .amount(amount)
                .method(PaymentMethod.MPESA_STK)
                .status(RentPaymentStatus.PENDING)
                .build();
        try {
            record = payments.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            return toResponse(payments.findByPaymentId(initiated.getId())
                    .orElseThrow(() -> new ConflictException("A payment for this invoice is already in progress.")));
        }
        return toResponse(record);
    }

    public RentPaymentResponse recordManual(UUID landlordId, UUID invoiceId, RecordPaymentRequest req) {
        RentInvoice invoice = invoiceService.requireOwned(landlordId, invoiceId);
        if (invoice.getStatus() == InvoiceStatus.WRITTEN_OFF) {
            throw new ConflictException("This invoice has been written off.");
        }
        if (req.getAmount().compareTo(invoice.getBalance()) > 0) {
            throw new ConflictException("That is more than the " + invoice.getBalance() + " still outstanding.");
        }

        PaymentMethod method = parseMethod(req.getMethod());
        if (method == PaymentMethod.MPESA_STK) {
            throw new ConflictException("An M-Pesa prompt is started by the tenant, not recorded by hand.");
        }

        RentPayment record = payments.save(RentPayment.builder()
                .invoiceId(invoice.getId())
                .leaseId(invoice.getLeaseId())
                .amount(req.getAmount())
                .method(method)
                .status(RentPaymentStatus.CONFIRMED)
                .mpesaReceipt(req.getMpesaReceipt())
                .recordedBy(landlordId)
                .note(req.getNote())
                .paidAt(LocalDateTime.now())
                .build());

        applyToInvoice(invoice, req.getAmount());
        return toResponse(record);
    }

    public void settleFromPayment(UUID paymentId, BigDecimal amount, String mpesaReceipt, String billRefNumber) {
        RentPayment found = payments.findByPaymentId(paymentId).orElse(null);
        if (found == null && StringUtils.hasText(billRefNumber)) {
            found = openPaybillRecord(paymentId, amount, billRefNumber);
        }
        final RentPayment record = found;

        if (record == null) {
            log.debug("PAYMENT_COMPLETED for {} has no matching rent payment; not a rent payment we started", paymentId);
            return;
        }
        if (record.getStatus() == RentPaymentStatus.CONFIRMED) {
            log.debug("Rent payment for {} already confirmed", paymentId);
            return;
        }

        if (amount != null && record.getMethod() != PaymentMethod.MPESA_PAYBILL) {
            record.setAmount(amount);
        }
        record.setStatus(RentPaymentStatus.CONFIRMED);
        record.setMpesaReceipt(mpesaReceipt);
        record.setPaidAt(LocalDateTime.now());
        payments.save(record);

        invoices.findById(record.getInvoiceId()).ifPresent(invoice ->
                applyToInvoice(invoice, record.getAmount()));
    }

    private RentPayment openPaybillRecord(UUID paymentId, BigDecimal amount, String billRefNumber) {
        RentInvoice invoice = invoices.findByInvoiceNumber(billRefNumber.trim().toUpperCase(Locale.ROOT)).orElse(null);
        if (invoice == null) {
            log.error("ALERT: paybill payment {} quotes account '{}', which matches no invoice. "
                    + "The money has arrived and needs manual allocation.", paymentId, billRefNumber);
            return null;
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("ALERT: paybill payment {} for invoice {} carries no usable amount",
                    paymentId, invoice.getInvoiceNumber());
            return null;
        }

        BigDecimal credited = amount.min(invoice.getBalance());
        if (credited.compareTo(amount) < 0) {
            log.warn("Paybill payment {} of {} exceeds the {} outstanding on invoice {}; crediting the balance only "
                            + "and leaving {} for manual handling",
                    paymentId, amount, invoice.getBalance(), invoice.getInvoiceNumber(), amount.subtract(credited));
        }
        if (credited.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Paybill payment {} arrived for invoice {}, which is already settled", paymentId, invoice.getInvoiceNumber());
            return null;
        }

        try {
            return payments.saveAndFlush(RentPayment.builder()
                    .invoiceId(invoice.getId())
                    .leaseId(invoice.getLeaseId())
                    .paymentId(paymentId)
                    .amount(credited)
                    .method(PaymentMethod.MPESA_PAYBILL)
                    .status(RentPaymentStatus.PENDING)
                    .build());
        } catch (DataIntegrityViolationException e) {
            return payments.findByPaymentId(paymentId).orElse(null);
        }
    }

    private void applyToInvoice(RentInvoice invoice, BigDecimal amount) {
        invoice.setAmountPaid(invoice.getAmountPaid().add(amount));

        if (invoice.getAmountPaid().compareTo(invoice.getAmountDue()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else if (invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus(invoice.getDueDate().isBefore(LocalDate.now())
                    ? InvoiceStatus.OVERDUE : InvoiceStatus.PARTIAL);
        }
        invoices.save(invoice);

        publisher.publishRentReceived(invoice, amount, tenantUserId(invoice.getTenantId()));
        log.info("Invoice {} now {} ({} of {})", invoice.getInvoiceNumber(), invoice.getStatus(),
                invoice.getAmountPaid(), invoice.getAmountDue());
    }

    private UUID tenantUserId(UUID tenantId) {
        return tenants.findById(tenantId).map(Tenant::getUserId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<RentPaymentResponse> forInvoice(UUID landlordId, UUID invoiceId) {
        invoiceService.requireOwned(landlordId, invoiceId);
        return payments.findByInvoiceIdOrderByCreatedAtDesc(invoiceId).stream().map(this::toResponse).toList();
    }

    private PaymentMethod parseMethod(String raw) {
        try {
            return PaymentMethod.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Unknown payment method '" + raw
                    + "'. Use MPESA_PAYBILL, BANK or CASH.");
        }
    }

    private RentPaymentResponse toResponse(RentPayment p) {
        return RentPaymentResponse.builder()
                .id(p.getId())
                .invoiceId(p.getInvoiceId())
                .paymentId(p.getPaymentId())
                .amount(p.getAmount())
                .method(p.getMethod().name())
                .status(p.getStatus().name())
                .mpesaReceipt(p.getMpesaReceipt())
                .note(p.getNote())
                .createdAt(p.getCreatedAt())
                .paidAt(p.getPaidAt())
                .build();
    }
}
