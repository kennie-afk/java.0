package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.dto.*;
import com.kenyarealestate.pms.entity.Lease;
import com.kenyarealestate.pms.entity.LeaseStatus;
import com.kenyarealestate.pms.entity.Tenant;
import com.kenyarealestate.pms.exception.ConflictException;
import com.kenyarealestate.pms.exception.ForbiddenException;
import com.kenyarealestate.pms.exception.NotFoundException;
import com.kenyarealestate.pms.repository.LeaseRepository;
import com.kenyarealestate.pms.repository.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@Transactional
public class TenantService {

    private final TenantRepository tenants;
    private final LeaseRepository leases;

    public TenantService(TenantRepository tenants, LeaseRepository leases) {
        this.tenants = tenants;
        this.leases = leases;
    }

    public TenantResponse create(UUID landlordId, CreateTenantRequest req) {
        String phone = normalise(req.getPhone());
        tenants.findByLandlordIdAndPhone(landlordId, phone).ifPresent(t -> {
            throw new ConflictException("You already have a tenant on " + phone + " (" + t.getFullName() + ").");
        });

        return toResponse(tenants.save(Tenant.builder()
                .landlordId(landlordId)
                .fullName(req.getFullName().trim())
                .phone(phone)
                .email(StringUtils.hasText(req.getEmail()) ? req.getEmail().trim().toLowerCase() : null)
                .nationalId(req.getNationalId())
                .emergencyName(req.getEmergencyName())
                .emergencyPhone(req.getEmergencyPhone())
                .build()));
    }

    @Transactional(readOnly = true)
    public Page<TenantResponse> listMine(UUID landlordId, String query, Pageable pageable) {
        Page<Tenant> page = StringUtils.hasText(query)
                ? tenants.search(landlordId, query.trim(), pageable)
                : tenants.findByLandlordIdOrderByFullNameAsc(landlordId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TenantResponse get(UUID landlordId, UUID tenantId) {
        return toResponse(owned(landlordId, tenantId));
    }

    public TenantResponse update(UUID landlordId, UUID tenantId, UpdateTenantRequest req) {
        Tenant t = owned(landlordId, tenantId);

        if (StringUtils.hasText(req.getPhone())) {
            String phone = normalise(req.getPhone());
            if (!phone.equals(t.getPhone())) {
                tenants.findByLandlordIdAndPhone(landlordId, phone).ifPresent(other -> {
                    throw new ConflictException("Another of your tenants already uses " + phone + ".");
                });
                t.setPhone(phone);
            }
        }
        if (StringUtils.hasText(req.getFullName())) t.setFullName(req.getFullName().trim());
        if (req.getEmail() != null)          t.setEmail(StringUtils.hasText(req.getEmail()) ? req.getEmail().trim().toLowerCase() : null);
        if (req.getNationalId() != null)     t.setNationalId(req.getNationalId());
        if (req.getEmergencyName() != null)  t.setEmergencyName(req.getEmergencyName());
        if (req.getEmergencyPhone() != null) t.setEmergencyPhone(req.getEmergencyPhone());

        return toResponse(tenants.save(t));
    }

    public TenantResponse linkUser(UUID landlordId, UUID tenantId, UUID userId) {
        Tenant t = owned(landlordId, tenantId);
        t.setUserId(userId);
        return toResponse(tenants.save(t));
    }

    public void delete(UUID landlordId, UUID tenantId) {
        Tenant t = owned(landlordId, tenantId);
        if (leases.existsByTenantId(tenantId)) {
            throw new ConflictException("This tenant has lease history and cannot be deleted.");
        }
        tenants.delete(t);
    }

    Tenant owned(UUID landlordId, UUID tenantId) {
        Tenant t = tenants.findById(tenantId).orElseThrow(() -> new NotFoundException("Tenant not found"));
        if (!t.getLandlordId().equals(landlordId)) {
            throw new ForbiddenException("This tenant belongs to another landlord.");
        }
        return t;
    }

    private String normalise(String phone) {
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) digits = digits.substring(1);
        if (digits.startsWith("0") && digits.length() == 10) digits = "254" + digits.substring(1);
        return digits;
    }

    TenantResponse toResponse(Tenant t) {
        boolean active = leases.findByTenantIdInOrderByCreatedAtDesc(java.util.List.of(t.getId()), Pageable.unpaged())
                .stream().anyMatch(l -> l.getStatus() == LeaseStatus.ACTIVE);
        return TenantResponse.builder()
                .id(t.getId())
                .landlordId(t.getLandlordId())
                .userId(t.getUserId())
                .fullName(t.getFullName())
                .phone(t.getPhone())
                .email(t.getEmail())
                .nationalId(t.getNationalId())
                .emergencyName(t.getEmergencyName())
                .emergencyPhone(t.getEmergencyPhone())
                .hasActiveLease(active)
                .createdAt(t.getCreatedAt())
                .build();
    }
}
