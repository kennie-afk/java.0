package com.kenyarealestate.pms.service;

import java.math.BigDecimal;
import com.kenyarealestate.pms.dto.*;
import com.kenyarealestate.pms.entity.*;
import com.kenyarealestate.pms.exception.ConflictException;
import com.kenyarealestate.pms.exception.ForbiddenException;
import com.kenyarealestate.pms.exception.NotFoundException;
import com.kenyarealestate.pms.kafka.PmsEventPublisher;
import com.kenyarealestate.pms.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@Transactional
public class MaintenanceService {

    private static final Map<MaintenanceStatus, Set<MaintenanceStatus>> ALLOWED_MOVES = Map.of(
            MaintenanceStatus.OPEN,         EnumSet.of(MaintenanceStatus.ACKNOWLEDGED, MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.REJECTED),
            MaintenanceStatus.ACKNOWLEDGED, EnumSet.of(MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.RESOLVED, MaintenanceStatus.REJECTED),
            MaintenanceStatus.IN_PROGRESS,  EnumSet.of(MaintenanceStatus.RESOLVED, MaintenanceStatus.REJECTED),
            MaintenanceStatus.RESOLVED,     EnumSet.of(MaintenanceStatus.CLOSED, MaintenanceStatus.IN_PROGRESS),
            MaintenanceStatus.CLOSED,       EnumSet.noneOf(MaintenanceStatus.class),
            MaintenanceStatus.REJECTED,     EnumSet.noneOf(MaintenanceStatus.class));

    private final MaintenanceRequestRepository requests;
    private final UnitRepository units;
    private final TenantRepository tenants;
    private final LeaseRepository leases;
    private final PmsEventPublisher publisher;

    public MaintenanceService(MaintenanceRequestRepository requests, UnitRepository units,
                              TenantRepository tenants, LeaseRepository leases, PmsEventPublisher publisher) {
        this.requests = requests;
        this.units = units;
        this.tenants = tenants;
        this.leases = leases;
        this.publisher = publisher;
    }

    public MaintenanceResponse raiseAsTenant(UUID userId, RaiseMaintenanceRequest req) {
        Tenant tenant = tenants.findAll().stream()
                .filter(t -> userId.equals(t.getUserId()))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException(
                        "No tenancy is linked to this account. Ask your landlord to link your tenant record."));

        Lease lease = leases.findByStatus(LeaseStatus.ACTIVE).stream()
                .filter(l -> l.getTenantId().equals(tenant.getId()))
                .findFirst()
                .orElseThrow(() -> new ConflictException("You do not have an active lease to raise a request against."));

        Unit unit = units.findById(lease.getUnitId())
                .orElseThrow(() -> new NotFoundException("Unit not found"));

        return toResponse(save(req, unit, lease, tenant.getId(), lease.getLandlordId(),
                userId, RaisedByRole.TENANT), true);
    }

    public MaintenanceResponse raiseAsLandlord(UUID landlordId, RaiseMaintenanceRequest req) {
        if (req.getUnitId() == null) {
            throw new ConflictException("Choose which unit this request is about.");
        }
        Unit unit = units.findById(req.getUnitId())
                .orElseThrow(() -> new NotFoundException("Unit not found"));
        if (!unit.getLandlordId().equals(landlordId)) {
            throw new ForbiddenException("This unit belongs to another landlord.");
        }
        Lease lease = leases.findByUnitIdAndStatus(unit.getId(), LeaseStatus.ACTIVE).orElse(null);

        return toResponse(save(req, unit, lease,
                lease != null ? lease.getTenantId() : null, landlordId,
                landlordId, RaisedByRole.LANDLORD), false);
    }

    private MaintenanceRequest save(RaiseMaintenanceRequest req, Unit unit, Lease lease,
                                    UUID tenantId, UUID landlordId, UUID raisedBy, RaisedByRole role) {
        MaintenanceRequest saved = requests.save(MaintenanceRequest.builder()
                .unitId(unit.getId())
                .leaseId(lease != null ? lease.getId() : null)
                .tenantId(tenantId)
                .landlordId(landlordId)
                .raisedBy(raisedBy)
                .raisedByRole(role)
                .reference(reference(unit))
                .category(req.getCategory().trim())
                .priority(parsePriority(req.getPriority()))
                .title(req.getTitle().trim())
                .description(req.getDescription().trim())
                .imageUrls(joinImages(req.getImageUrls()))
                .build());

        publisher.publishMaintenanceRaised(saved, unit.getLabel(), tenantUserId(tenantId));
        return saved;
    }

    private String reference(Unit unit) {
        String label = unit.getLabel().replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (label.length() > 6) label = label.substring(0, 6);
        if (label.isEmpty()) label = "UNIT";
        return "MNT-" + label + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    public MaintenanceResponse update(UUID landlordId, UUID requestId, UpdateMaintenanceRequest req) {
        MaintenanceRequest r = owned(landlordId, requestId);

        if (StringUtils.hasText(req.getStatus())) {
            MaintenanceStatus target = parseStatus(req.getStatus());
            if (target != r.getStatus()) {
                Set<MaintenanceStatus> allowed = ALLOWED_MOVES.getOrDefault(r.getStatus(), Set.of());
                if (!allowed.contains(target)) {
                    String from = readable(r.getStatus());
                    String article = "aeiou".indexOf(from.charAt(0)) >= 0 ? "An " : "A ";
                    throw new ConflictException(article + from + " request cannot become " + readable(target) + ".");
                }
                applyStatus(r, target);
            }
        }
        if (StringUtils.hasText(req.getPriority()))        r.setPriority(parsePriority(req.getPriority()));
        if (req.getAssignedTo() != null)                   r.setAssignedTo(req.getAssignedTo());
        if (req.getResolutionNotes() != null)              r.setResolutionNotes(req.getResolutionNotes());
        if (req.getCost() != null)                         r.setCost(req.getCost());

        applyCostAttribution(r, req);

        return toResponse(requests.save(r), false);
    }

    /**
     * Records who pays, and refuses the combinations that are not a decision.
     *
     * <p>A cost with no bearer is the state this exists to eliminate: it tells you a
     * repair was expensive without telling you whose expense it was, which settles no
     * argument and cannot feed a deposit deduction later. Equally, a SHARED job with no
     * split is an unfinished thought, and a landlord-borne job with a tenant charge is a
     * contradiction. All three are rejected here and again by the database.
     */
    private void applyCostAttribution(MaintenanceRequest r, UpdateMaintenanceRequest req) {
        if (StringUtils.hasText(req.getCostBorneBy())) {
            CostBearer bearer;
            try {
                bearer = CostBearer.valueOf(req.getCostBorneBy().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ConflictException("Cost must be borne by LANDLORD, TENANT or SHARED.");
            }
            r.setCostBorneBy(bearer);
        }
        if (req.getTenantCharge() != null) {
            r.setTenantCharge(req.getTenantCharge());
        }

        CostBearer bearer = r.getCostBorneBy();
        if (bearer == null) {
            return;
        }

        BigDecimal cost = r.getCost() == null ? BigDecimal.ZERO : r.getCost();

        switch (bearer) {
            case LANDLORD -> r.setTenantCharge(null);
            // The tenant pays all of it, so the charge is the cost. Deriving it rather
            // than asking removes a field the caller can get wrong.
            case TENANT   -> r.setTenantCharge(cost);
            case SHARED   -> {
                if (r.getTenantCharge() == null) {
                    throw new ConflictException(
                            "Say how much of this the tenant is paying, or mark it as borne by one side.");
                }
                if (r.getTenantCharge().compareTo(cost) > 0) {
                    throw new ConflictException(
                            "The tenant's share cannot be more than the repair cost.");
                }
            }
        }
    }

    private void applyStatus(MaintenanceRequest r, MaintenanceStatus target) {
        LocalDateTime now = LocalDateTime.now();
        r.setStatus(target);
        switch (target) {
            case ACKNOWLEDGED -> r.setAcknowledgedAt(now);
            case RESOLVED -> {
                r.setResolvedAt(now);
                publisher.publishMaintenanceResolved(r, unitLabel(r.getUnitId()), tenantUserId(r.getTenantId()));
            }
            case CLOSED -> r.setClosedAt(now);
            case REJECTED -> {
                r.setClosedAt(now);
                publisher.publishMaintenanceResolved(r, unitLabel(r.getUnitId()), tenantUserId(r.getTenantId()));
            }
            case IN_PROGRESS -> {
                if (r.getAcknowledgedAt() == null) r.setAcknowledgedAt(now);
                r.setResolvedAt(null);
            }
            default -> { }
        }
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceResponse> listForLandlord(UUID landlordId, String status, Pageable pageable) {
        Page<MaintenanceRequest> page = StringUtils.hasText(status)
                ? requests.findByLandlordIdAndStatusOrderByCreatedAtDesc(landlordId, parseStatus(status), pageable)
                : requests.findByLandlordIdOrderByCreatedAtDesc(landlordId, pageable);
        return page.map(r -> toResponse(r, false));
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceResponse> listForTenantUser(UUID userId, Pageable pageable) {
        List<UUID> tenantIds = tenants.findAll().stream()
                .filter(t -> userId.equals(t.getUserId()))
                .map(Tenant::getId)
                .toList();
        if (tenantIds.isEmpty()) return Page.empty(pageable);
        return requests.findByTenantIdInOrderByCreatedAtDesc(tenantIds, pageable).map(r -> toResponse(r, true));
    }

    @Transactional(readOnly = true)
    public String photoKeyFor(UUID callerId, UUID requestId, int index) {
        MaintenanceRequest r = requests.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Maintenance request not found"));

        boolean isLandlord = r.getLandlordId().equals(callerId);
        boolean isTenant = r.getTenantId() != null
                && tenants.findById(r.getTenantId()).map(t -> callerId.equals(t.getUserId())).orElse(false);
        if (!isLandlord && !isTenant) {
            throw new ForbiddenException("This request is not yours to look at.");
        }

        List<String> urls = splitImages(r.getImageUrls());
        if (index < 0 || index >= urls.size()) {
            throw new NotFoundException("No photo at that position");
        }
        String key = com.kenyarealestate.pms.client.DocumentClient.objectKeyFrom(urls.get(index));
        if (key == null) {
            throw new NotFoundException("That photo cannot be served");
        }
        return key;
    }

    @Transactional(readOnly = true)
    public long openCount(UUID landlordId) {
        return requests.countByLandlordIdAndStatusIn(landlordId,
                List.of(MaintenanceStatus.OPEN, MaintenanceStatus.ACKNOWLEDGED, MaintenanceStatus.IN_PROGRESS));
    }

    MaintenanceRequest owned(UUID landlordId, UUID requestId) {
        MaintenanceRequest r = requests.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Maintenance request not found"));
        if (!r.getLandlordId().equals(landlordId)) {
            throw new ForbiddenException("This request belongs to another landlord.");
        }
        return r;
    }

    private UUID tenantUserId(UUID tenantId) {
        if (tenantId == null) return null;
        return tenants.findById(tenantId).map(Tenant::getUserId).orElse(null);
    }

    private String unitLabel(UUID unitId) {
        return units.findById(unitId).map(Unit::getLabel).orElse(null);
    }

    private String joinImages(List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;
        return String.join("\n", urls.stream().filter(StringUtils::hasText).limit(6).toList());
    }

    private List<String> splitImages(String joined) {
        if (!StringUtils.hasText(joined)) return List.of();
        return Arrays.stream(joined.split("\n")).filter(StringUtils::hasText).toList();
    }

    private MaintenancePriority parsePriority(String raw) {
        if (!StringUtils.hasText(raw)) return MaintenancePriority.MEDIUM;
        try {
            return MaintenancePriority.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Unknown priority '" + raw + "'. Use LOW, MEDIUM, HIGH or URGENT.");
        }
    }

    private MaintenanceStatus parseStatus(String raw) {
        try {
            return MaintenanceStatus.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Unknown status '" + raw
                    + "'. Use OPEN, ACKNOWLEDGED, IN_PROGRESS, RESOLVED, CLOSED or REJECTED.");
        }
    }

    private String readable(MaintenanceStatus s) {
        return s.name().replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    MaintenanceResponse toResponse(MaintenanceRequest r, boolean forTenant) {
        Tenant tenant = r.getTenantId() != null ? tenants.findById(r.getTenantId()).orElse(null) : null;
        return MaintenanceResponse.builder()
                .id(r.getId())
                .unitId(r.getUnitId())
                .unitLabel(unitLabel(r.getUnitId()))
                .leaseId(r.getLeaseId())
                .tenantId(r.getTenantId())
                .tenantName(forTenant ? null : (tenant != null ? tenant.getFullName() : null))
                .landlordId(r.getLandlordId())
                .reference(r.getReference())
                .category(r.getCategory())
                .priority(r.getPriority().name())
                .title(r.getTitle())
                .description(r.getDescription())
                .imageUrls(splitImages(r.getImageUrls()))
                .status(r.getStatus().name())
                .raisedByRole(r.getRaisedByRole().name())
                .assignedTo(r.getAssignedTo())
                .resolutionNotes(r.getResolutionNotes())
                .cost(r.getCost())
                .costBorneBy(r.getCostBorneBy() == null ? null : r.getCostBorneBy().name())
                .tenantCharge(r.getTenantCharge())
                .createdAt(r.getCreatedAt())
                .acknowledgedAt(r.getAcknowledgedAt())
                .resolvedAt(r.getResolvedAt())
                .closedAt(r.getClosedAt())
                .build();
    }
}
