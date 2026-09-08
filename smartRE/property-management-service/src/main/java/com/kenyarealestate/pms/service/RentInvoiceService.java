package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.dto.*;
import com.kenyarealestate.pms.entity.*;
import com.kenyarealestate.pms.exception.ConflictException;
import com.kenyarealestate.pms.exception.ForbiddenException;
import com.kenyarealestate.pms.exception.NotFoundException;
import com.kenyarealestate.pms.kafka.PmsEventPublisher;
import com.kenyarealestate.pms.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class RentInvoiceService {

    private final RentInvoiceRepository invoices;
    private final LeaseRepository leases;
    private final TenantRepository tenants;
    private final UnitRepository units;
    private final PmsEventPublisher publisher;
    private final int graceDays;
    private final int leadDays;

    public RentInvoiceService(RentInvoiceRepository invoices, LeaseRepository leases,
                              TenantRepository tenants, UnitRepository units,
                              PmsEventPublisher publisher,
                              @Value("${pms.invoice.grace-days:5}") int graceDays,
                              @Value("${pms.invoice.lead-days:7}") int leadDays) {
        this.invoices = invoices;
        this.leases = leases;
        this.tenants = tenants;
        this.units = units;
        this.publisher = publisher;
        this.graceDays = graceDays;
        this.leadDays = leadDays;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<RentInvoice> issueIfDue(Lease lease, LocalDate today) {
        if (lease.getStatus() != LeaseStatus.ACTIVE) return Optional.empty();

        LocalDate periodStart = BillingPeriods.firstPeriodStart(lease);
        LocalDate horizon = today.plusDays(leadDays);

        RentInvoice issued = null;
        while (!periodStart.isAfter(horizon)) {
            if (lease.getEndDate() != null && periodStart.isAfter(lease.getEndDate())) break;

            if (invoices.findByLeaseIdAndPeriodStart(lease.getId(), periodStart).isEmpty()) {
                RentInvoice invoice = build(lease, periodStart);
                try {
                    issued = invoices.saveAndFlush(invoice);
                    publisher.publishRentInvoiceIssued(issued, tenantUserId(issued.getTenantId()));
                    log.info("Issued invoice {} for lease {} period {}",
                            issued.getInvoiceNumber(), lease.getId(), periodStart);
                } catch (DataIntegrityViolationException e) {
                    log.debug("Invoice for lease {} period {} already exists", lease.getId(), periodStart);
                }
            }
            periodStart = BillingPeriods.nextPeriodStart(periodStart, lease.getPaymentFrequency());
        }
        return Optional.ofNullable(issued);
    }

    private RentInvoice build(Lease lease, LocalDate periodStart) {
        Unit unit = units.findById(lease.getUnitId())
                .orElseThrow(() -> new NotFoundException("Unit not found for lease " + lease.getId()));
        return RentInvoice.builder()
                .leaseId(lease.getId())
                .unitId(lease.getUnitId())
                .tenantId(lease.getTenantId())
                .landlordId(lease.getLandlordId())
                .invoiceNumber(invoiceNumber(unit, periodStart))
                .periodStart(periodStart)
                .periodEnd(BillingPeriods.periodEnd(periodStart, lease.getPaymentFrequency()))
                .dueDate(periodStart.plusDays(graceDays))
                .amountDue(BillingPeriods.amountFor(lease))
                .build();
    }

    private String invoiceNumber(Unit unit, LocalDate periodStart) {
        String label = unit.getLabel().replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (label.length() > 6) label = label.substring(0, 6);
        if (label.isEmpty()) label = "UNIT";
        return "RNT-" + periodStart.getYear() + String.format("%02d", periodStart.getMonthValue())
                + "-" + label + "-" + unit.getId().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private UUID tenantUserId(UUID tenantId) {
        return tenants.findById(tenantId).map(Tenant::getUserId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> listForLandlord(UUID landlordId, String status, Pageable pageable) {
        Page<RentInvoice> page = StringUtils.hasText(status)
                ? invoices.findByLandlordIdAndStatusOrderByDueDateDesc(landlordId, parseStatus(status), pageable)
                : invoices.findByLandlordIdOrderByDueDateDesc(landlordId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> listForTenantUser(UUID userId, Pageable pageable) {
        List<UUID> tenantIds = tenantIdsFor(userId);
        if (tenantIds.isEmpty()) return Page.empty(pageable);
        return invoices.findByTenantIdInOrderByDueDateDesc(tenantIds, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listForLease(UUID landlordId, UUID leaseId) {
        Lease lease = leases.findById(leaseId).orElseThrow(() -> new NotFoundException("Lease not found"));
        if (!lease.getLandlordId().equals(landlordId)) {
            throw new ForbiddenException("This lease belongs to another landlord.");
        }
        return invoices.findByLeaseIdOrderByPeriodStartDesc(leaseId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RentInvoiceRefResponse resolveByReference(String reference) {
        RentInvoice invoice = invoices.findByInvoiceNumber(reference.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("No rent invoice with that account number"));
        Unit unit = units.findById(invoice.getUnitId()).orElse(null);
        UUID tenantUserId = tenants.findById(invoice.getTenantId()).map(Tenant::getUserId).orElse(null);

        return RentInvoiceRefResponse.builder()
                .invoiceId(invoice.getId())
                .leaseId(invoice.getLeaseId())
                .unitId(invoice.getUnitId())
                .tenantId(invoice.getTenantId())
                .tenantUserId(tenantUserId)
                .landlordId(invoice.getLandlordId())
                .propertyId(unit != null ? unit.getPropertyId() : null)
                .invoiceNumber(invoice.getInvoiceNumber())
                .balance(invoice.getBalance())
                .status(invoice.getStatus().name())
                .build();
    }

    @Transactional(readOnly = true)
    /** The tenant records belonging to one account, across every landlord they rent from. */
    private List<UUID> tenantIdsFor(UUID userId) {
        return tenants.findByUserId(userId).stream().map(Tenant::getId).toList();
    }

    /**
     * An invoice the caller is entitled to see, as either side of it.
     *
     * <p>{@link #requireOwned} is the landlord's check and stays that way — it guards
     * writes, and a tenant must never write to an invoice. This is the read equivalent:
     * the tenant an invoice is addressed to has an obvious right to look at it, and
     * without this they cannot see the payments they themselves made.
     */
    @Transactional(readOnly = true)
    public RentInvoice requireVisibleTo(UUID userId, UUID invoiceId) {
        RentInvoice invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> new NotFoundException("Invoice not found"));
        if (invoice.getLandlordId().equals(userId)) return invoice;
        if (tenantIdsFor(userId).contains(invoice.getTenantId())) return invoice;
        throw new ForbiddenException("This invoice belongs to another tenancy.");
    }

    public RentInvoice requireOwned(UUID landlordId, UUID invoiceId) {
        RentInvoice invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> new NotFoundException("Invoice not found"));
        if (!invoice.getLandlordId().equals(landlordId)) {
            throw new ForbiddenException("This invoice belongs to another landlord.");
        }
        return invoice;
    }

    public InvoiceResponse writeOff(UUID landlordId, UUID invoiceId) {
        RentInvoice invoice = requireOwned(landlordId, invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("This invoice is already settled.");
        }
        invoice.setStatus(InvoiceStatus.WRITTEN_OFF);
        return toResponse(invoices.save(invoice));
    }

    @Transactional(readOnly = true)
    public BigDecimal outstandingFor(UUID landlordId) {
        BigDecimal v = invoices.outstandingFor(landlordId);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Transactional(readOnly = true)
    public long overdueCount(UUID landlordId) {
        return invoices.countByLandlordIdAndStatus(landlordId, InvoiceStatus.OVERDUE);
    }

    private InvoiceStatus parseStatus(String raw) {
        try {
            return InvoiceStatus.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Unknown invoice status '" + raw
                    + "'. Use PENDING, PARTIAL, PAID, OVERDUE or WRITTEN_OFF.");
        }
    }

    public InvoiceResponse toResponse(RentInvoice i) {
        Unit unit = units.findById(i.getUnitId()).orElse(null);
        Tenant tenant = tenants.findById(i.getTenantId()).orElse(null);
        return InvoiceResponse.builder()
                .id(i.getId())
                .leaseId(i.getLeaseId())
                .unitId(i.getUnitId())
                .unitLabel(unit != null ? unit.getLabel() : null)
                .tenantId(i.getTenantId())
                .tenantName(tenant != null ? tenant.getFullName() : null)
                .tenantPhone(tenant != null ? tenant.getPhone() : null)
                .invoiceNumber(i.getInvoiceNumber())
                .periodStart(i.getPeriodStart())
                .periodEnd(i.getPeriodEnd())
                .dueDate(i.getDueDate())
                .amountDue(i.getAmountDue())
                .amountPaid(i.getAmountPaid())
                .balance(i.getBalance())
                .status(i.getStatus().name())
                .createdAt(i.getCreatedAt())
                .build();
    }
}
