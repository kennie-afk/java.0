package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.dto.*;
import com.kenyarealestate.pms.entity.*;
import com.kenyarealestate.pms.exception.ConflictException;
import com.kenyarealestate.pms.exception.ForbiddenException;
import com.kenyarealestate.pms.exception.NotFoundException;
import com.kenyarealestate.pms.kafka.PmsEventPublisher;
import com.kenyarealestate.pms.repository.LeaseRepository;
import com.kenyarealestate.pms.repository.TenantRepository;
import com.kenyarealestate.pms.repository.UnitRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public class LeaseService {

    private final LeaseRepository leases;
    private final UnitRepository units;
    private final TenantRepository tenants;
    private final UnitService unitService;
    private final TenantService tenantService;
    private final PmsEventPublisher publisher;
    private final BigDecimal defaultFeePct;
    private final int defaultNoticeDays;

    public LeaseService(LeaseRepository leases, UnitRepository units, TenantRepository tenants,
                        UnitService unitService, TenantService tenantService, PmsEventPublisher publisher,
                        @Value("${pms.management-fee-pct:10.0}") BigDecimal defaultFeePct,
                        @Value("${pms.default-notice-period-days:30}") int defaultNoticeDays) {
        this.leases = leases;
        this.units = units;
        this.tenants = tenants;
        this.unitService = unitService;
        this.tenantService = tenantService;
        this.publisher = publisher;
        this.defaultFeePct = defaultFeePct;
        this.defaultNoticeDays = defaultNoticeDays;
    }

    public LeaseResponse create(UUID landlordId, CreateLeaseRequest req) {
        Unit unit = unitService.ownedUnit(landlordId, req.getUnitId());
        Tenant tenant = tenantService.owned(landlordId, req.getTenantId());

        if (req.getEndDate() != null && !req.getEndDate().isAfter(req.getStartDate())) {
            throw new ConflictException("The lease end date must fall after its start date.");
        }

        Lease lease = leases.save(Lease.builder()
                .unitId(unit.getId())
                .tenantId(tenant.getId())
                .landlordId(landlordId)
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .rentAmount(req.getRentAmount() != null ? req.getRentAmount() : unit.getRentAmount())
                .depositAmount(req.getDepositAmount() != null ? req.getDepositAmount()
                        : (unit.getDepositAmount() != null ? unit.getDepositAmount() : BigDecimal.ZERO))
                .managementFeePct(req.getManagementFeePct() != null ? req.getManagementFeePct() : defaultFeePct)
                .billingDay(req.getBillingDay() != null ? req.getBillingDay() : req.getStartDate().getDayOfMonth() > 28 ? 1 : req.getStartDate().getDayOfMonth())
                .paymentFrequency(parseFrequency(req.getPaymentFrequency()))
                .noticePeriodDays(req.getNoticePeriodDays() != null ? req.getNoticePeriodDays() : defaultNoticeDays)
                .status(LeaseStatus.DRAFT)
                .build());

        return toResponse(lease);
    }

    public LeaseResponse activate(UUID landlordId, UUID leaseId) {
        Lease lease = owned(landlordId, leaseId);
        if (lease.getStatus() != LeaseStatus.DRAFT) {
            throw new ConflictException("Only a draft lease can be activated. This one is " + lease.getStatus() + ".");
        }

        lease.setStatus(LeaseStatus.ACTIVE);
        try {
            leases.saveAndFlush(lease);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("That unit already has an active lease. End it before starting a new one.");
        }

        Unit unit = units.findById(lease.getUnitId()).orElseThrow(() -> new NotFoundException("Unit not found"));
        unit.setStatus(UnitStatus.OCCUPIED);
        units.save(unit);

        publisher.publishLeaseActivated(lease, unit.getPropertyId(), tenantUserId(lease.getTenantId()));
        return toResponse(lease);
    }

    public LeaseResponse terminate(UUID landlordId, UUID leaseId, String reason) {
        Lease lease = owned(landlordId, leaseId);
        if (lease.getStatus() != LeaseStatus.ACTIVE && lease.getStatus() != LeaseStatus.DRAFT) {
            throw new ConflictException("This lease is already " + lease.getStatus() + ".");
        }

        boolean wasActive = lease.getStatus() == LeaseStatus.ACTIVE;
        lease.setStatus(LeaseStatus.TERMINATED);
        lease.setTerminatedReason(reason);
        lease.setTerminatedAt(LocalDateTime.now());
        leases.save(lease);

        if (wasActive) {
            releaseUnit(lease.getUnitId());
            publisher.publishLeaseEnded(lease, reason, tenantUserId(lease.getTenantId()));
        }
        return toResponse(lease);
    }

    public LeaseResponse end(UUID landlordId, UUID leaseId) {
        Lease lease = owned(landlordId, leaseId);
        if (lease.getStatus() != LeaseStatus.ACTIVE) {
            throw new ConflictException("Only an active lease can be ended. This one is " + lease.getStatus() + ".");
        }
        lease.setStatus(LeaseStatus.ENDED);
        leases.save(lease);
        releaseUnit(lease.getUnitId());
        publisher.publishLeaseEnded(lease, "Lease ran to completion", tenantUserId(lease.getTenantId()));
        return toResponse(lease);
    }

    public LeaseResponse renew(UUID landlordId, UUID leaseId, RenewLeaseRequest req) {
        Lease existing = owned(landlordId, leaseId);
        if (existing.getStatus() != LeaseStatus.ACTIVE && existing.getStatus() != LeaseStatus.ENDED) {
            throw new ConflictException("Only an active or ended lease can be renewed.");
        }
        if (req.getEndDate() != null && !req.getEndDate().isAfter(req.getStartDate())) {
            throw new ConflictException("The lease end date must fall after its start date.");
        }

        if (existing.getStatus() == LeaseStatus.ACTIVE) {
            existing.setStatus(LeaseStatus.RENEWED);
            leases.saveAndFlush(existing);
            releaseUnit(existing.getUnitId());
        }

        Lease renewal = leases.save(Lease.builder()
                .unitId(existing.getUnitId())
                .tenantId(existing.getTenantId())
                .landlordId(landlordId)
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .rentAmount(req.getRentAmount() != null ? req.getRentAmount() : existing.getRentAmount())
                .depositAmount(existing.getDepositAmount())
                .depositHeld(existing.getDepositHeld())
                .managementFeePct(existing.getManagementFeePct())
                .billingDay(existing.getBillingDay())
                .paymentFrequency(existing.getPaymentFrequency())
                .noticePeriodDays(existing.getNoticePeriodDays())
                .status(LeaseStatus.DRAFT)
                .build());

        return toResponse(renewal);
    }

    @Transactional(readOnly = true)
    public Page<LeaseResponse> listMine(UUID landlordId, String status, Pageable pageable) {
        Page<Lease> page = StringUtils.hasText(status)
                ? leases.findByLandlordIdAndStatusOrderByCreatedAtDesc(landlordId, parseStatus(status), pageable)
                : leases.findByLandlordIdOrderByCreatedAtDesc(landlordId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<LeaseResponse> listForTenantUser(UUID userId, Pageable pageable) {
        List<UUID> tenantIds = tenants.findAll().stream()
                .filter(t -> userId.equals(t.getUserId()))
                .map(Tenant::getId)
                .toList();
        if (tenantIds.isEmpty()) return Page.empty(pageable);
        return leases.findByTenantIdInOrderByCreatedAtDesc(tenantIds, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public LeaseResponse get(UUID landlordId, UUID leaseId) {
        return toResponse(owned(landlordId, leaseId));
    }

    @Transactional(readOnly = true)
    public List<LeaseResponse> historyForUnit(UUID landlordId, UUID unitId) {
        unitService.ownedUnit(landlordId, unitId);
        return leases.findByUnitIdOrderByStartDateDesc(unitId).stream().map(this::toResponse).toList();
    }

    private void releaseUnit(UUID unitId) {
        units.findById(unitId).ifPresent(u -> {
            u.setStatus(UnitStatus.VACANT);
            units.save(u);
        });
    }

    Lease owned(UUID landlordId, UUID leaseId) {
        Lease lease = leases.findById(leaseId).orElseThrow(() -> new NotFoundException("Lease not found"));
        if (!lease.getLandlordId().equals(landlordId)) {
            throw new ForbiddenException("This lease belongs to another landlord.");
        }
        return lease;
    }

    private LeaseStatus parseStatus(String raw) {
        try {
            return LeaseStatus.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Unknown lease status '" + raw
                    + "'. Use DRAFT, ACTIVE, ENDED, TERMINATED or RENEWED.");
        }
    }

    private PaymentFrequency parseFrequency(String raw) {
        if (!StringUtils.hasText(raw)) return PaymentFrequency.MONTHLY;
        try {
            return PaymentFrequency.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Unknown payment frequency '" + raw
                    + "'. Use MONTHLY, QUARTERLY or ANNUALLY.");
        }
    }

    LeaseResponse toResponse(Lease l) {
        Unit unit = units.findById(l.getUnitId()).orElse(null);
        Tenant tenant = tenants.findById(l.getTenantId()).orElse(null);
        return LeaseResponse.builder()
                .id(l.getId())
                .unitId(l.getUnitId())
                .unitLabel(unit != null ? unit.getLabel() : null)
                .propertyId(unit != null ? unit.getPropertyId() : null)
                .tenantId(l.getTenantId())
                .tenantName(tenant != null ? tenant.getFullName() : null)
                .tenantPhone(tenant != null ? tenant.getPhone() : null)
                .landlordId(l.getLandlordId())
                .startDate(l.getStartDate())
                .endDate(l.getEndDate())
                .rentAmount(l.getRentAmount())
                .depositAmount(l.getDepositAmount())
                .depositHeld(l.getDepositHeld())
                .managementFeePct(l.getManagementFeePct())
                .billingDay(l.getBillingDay())
                .paymentFrequency(l.getPaymentFrequency().name())
                .noticePeriodDays(l.getNoticePeriodDays())
                .status(l.getStatus().name())
                .terminatedReason(l.getTerminatedReason())
                .terminatedAt(l.getTerminatedAt())
                .createdAt(l.getCreatedAt())
                .build();
    }

    /**
     * The tenant's SmartRE account, or null when their record has never been linked to
     * one. Null is normal, not an error: a landlord can keep a tenant on the books
     * without that person ever creating an account, and the notification is simply
     * skipped rather than the lease change failing.
     */
    private UUID tenantUserId(UUID tenantId) {
        return tenants.findById(tenantId).map(com.kenyarealestate.pms.entity.Tenant::getUserId).orElse(null);
    }
}
