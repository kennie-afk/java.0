package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.client.PropertyServiceClient;
import com.kenyarealestate.pms.dto.*;
import com.kenyarealestate.pms.entity.*;
import com.kenyarealestate.pms.exception.ConflictException;
import com.kenyarealestate.pms.exception.ForbiddenException;
import com.kenyarealestate.pms.exception.NotFoundException;
import com.kenyarealestate.pms.repository.LeaseRepository;
import com.kenyarealestate.pms.repository.TenantRepository;
import com.kenyarealestate.pms.repository.UnitRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class UnitService {

    private final UnitRepository units;
    private final LeaseRepository leases;
    private final TenantRepository tenants;
    private final PropertyServiceClient propertyClient;
    private final RentInvoiceService invoiceService;
    private final MaintenanceService maintenanceService;

    public UnitService(UnitRepository units, LeaseRepository leases,
                       TenantRepository tenants, PropertyServiceClient propertyClient,
                       @org.springframework.context.annotation.Lazy RentInvoiceService invoiceService,
                       @org.springframework.context.annotation.Lazy MaintenanceService maintenanceService) {
        this.units = units;
        this.leases = leases;
        this.tenants = tenants;
        this.propertyClient = propertyClient;
        this.invoiceService = invoiceService;
        this.maintenanceService = maintenanceService;
    }

    public UnitResponse create(UUID landlordId, CreateUnitRequest req) {
        PropertySummary property = propertyClient.getProperty(req.getPropertyId());
        if (property == null) {
            throw new ConflictException("Could not confirm that property right now. Try again in a moment.");
        }
        if (!landlordId.equals(property.getSellerId())) {
            throw new ForbiddenException("You can only add units to a property you own.");
        }
        if (units.existsByPropertyIdAndLabelIgnoreCase(req.getPropertyId(), req.getLabel().trim())) {
            throw new ConflictException("A unit called '" + req.getLabel().trim() + "' already exists on this property.");
        }

        Unit unit = units.save(Unit.builder()
                .propertyId(req.getPropertyId())
                .landlordId(landlordId)
                .label(req.getLabel().trim())
                .unitType(req.getUnitType())
                .bedrooms(req.getBedrooms())
                .bathrooms(req.getBathrooms())
                .sizeSqm(req.getSizeSqm())
                .rentAmount(req.getRentAmount())
                .depositAmount(req.getDepositAmount())
                .notes(req.getNotes())
                .build());
        return toResponse(unit);
    }

    /**
     * @param propertyId scope to one building, or null for every unit the landlord owns.
     *                   A landlord with a hundred properties does not think in a flat
     *                   list of four hundred units; they think "Riverside Court, which
     *                   are vacant".
     * @param q          free text over label, type and notes. Blank is treated as absent
     *                   rather than as a search for the empty string.
     */
    @Transactional(readOnly = true)
    public Page<UnitResponse> listMine(UUID landlordId, UUID propertyId, String status,
                                       String q, Pageable pageable) {
        return units.search(
                landlordId,
                propertyId,
                StringUtils.hasText(status) ? parseStatus(status) : null,
                // "%" rather than null: see UnitRepository.search for why a null here
                // breaks on Postgres.
                StringUtils.hasText(q) ? "%" + q.trim().toLowerCase() + "%" : "%",
                pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> listByProperty(UUID landlordId, UUID propertyId) {
        return units.findByPropertyIdOrderByLabelAsc(propertyId).stream()
                .filter(u -> u.getLandlordId().equals(landlordId))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnitResponse get(UUID landlordId, UUID unitId) {
        return toResponse(ownedUnit(landlordId, unitId));
    }

    public UnitResponse update(UUID landlordId, UUID unitId, UpdateUnitRequest req) {
        Unit unit = ownedUnit(landlordId, unitId);

        if (StringUtils.hasText(req.getLabel())) {
            String label = req.getLabel().trim();
            if (!label.equalsIgnoreCase(unit.getLabel())
                    && units.existsByPropertyIdAndLabelIgnoreCase(unit.getPropertyId(), label)) {
                throw new ConflictException("A unit called '" + label + "' already exists on this property.");
            }
            unit.setLabel(label);
        }
        if (req.getUnitType() != null)       unit.setUnitType(req.getUnitType());
        if (req.getBedrooms() != null)       unit.setBedrooms(req.getBedrooms());
        if (req.getBathrooms() != null)      unit.setBathrooms(req.getBathrooms());
        if (req.getSizeSqm() != null)        unit.setSizeSqm(req.getSizeSqm());
        if (req.getRentAmount() != null)     unit.setRentAmount(req.getRentAmount());
        if (req.getDepositAmount() != null)  unit.setDepositAmount(req.getDepositAmount());
        if (req.getNotes() != null)          unit.setNotes(req.getNotes());

        if (StringUtils.hasText(req.getStatus())) {
            UnitStatus requested = parseStatus(req.getStatus());
            boolean occupied = leases.findByUnitIdAndStatus(unitId, LeaseStatus.ACTIVE).isPresent();
            if (occupied && requested != UnitStatus.OCCUPIED) {
                throw new ConflictException("This unit has an active lease. End the lease before changing its status.");
            }
            if (!occupied && requested == UnitStatus.OCCUPIED) {
                throw new ConflictException("Mark a unit occupied by activating a lease on it, not directly.");
            }
            unit.setStatus(requested);
        }
        return toResponse(units.save(unit));
    }

    public void delete(UUID landlordId, UUID unitId) {
        Unit unit = ownedUnit(landlordId, unitId);
        if (!leases.findByUnitIdOrderByStartDateDesc(unitId).isEmpty()) {
            throw new ConflictException("This unit has lease history and cannot be deleted. Mark it under maintenance instead.");
        }
        units.delete(unit);
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse summary(UUID landlordId) {
        long total       = units.countByLandlordId(landlordId);
        long occupied    = units.countByLandlordIdAndStatus(landlordId, UnitStatus.OCCUPIED);
        long vacant      = units.countByLandlordIdAndStatus(landlordId, UnitStatus.VACANT);
        long maintenance = units.countByLandlordIdAndStatus(landlordId, UnitStatus.UNDER_MAINTENANCE);
        long activeLeaseCount = leases.countByLandlordIdAndStatus(landlordId, LeaseStatus.ACTIVE);

        BigDecimal rentRoll = leases
                .findByLandlordIdAndStatusOrderByCreatedAtDesc(landlordId, LeaseStatus.ACTIVE, Pageable.unpaged())
                .stream()
                .map(Lease::getRentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double occupancy = total == 0 ? 0d
                : BigDecimal.valueOf(occupied)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                    .doubleValue();

        return PortfolioSummaryResponse.builder()
                .totalUnits(total)
                .occupiedUnits(occupied)
                .vacantUnits(vacant)
                .underMaintenanceUnits(maintenance)
                .tenants(tenants.countByLandlordId(landlordId))
                .activeLeases(activeLeaseCount)
                .monthlyRentRoll(rentRoll)
                .occupancyRate(occupancy)
                .outstandingRent(invoiceService.outstandingFor(landlordId))
                .overdueInvoices(invoiceService.overdueCount(landlordId))
                .openMaintenance(maintenanceService.openCount(landlordId))
                .build();
    }

    Unit ownedUnit(UUID landlordId, UUID unitId) {
        Unit unit = units.findById(unitId).orElseThrow(() -> new NotFoundException("Unit not found"));
        if (!unit.getLandlordId().equals(landlordId)) {
            throw new ForbiddenException("This unit belongs to another landlord.");
        }
        return unit;
    }

    private UnitStatus parseStatus(String raw) {
        try {
            return UnitStatus.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Unknown unit status '" + raw
                    + "'. Use VACANT, OCCUPIED, UNDER_MAINTENANCE or RESERVED.");
        }
    }

    UnitResponse toResponse(Unit u) {
        Optional<Lease> active = leases.findByUnitIdAndStatus(u.getId(), LeaseStatus.ACTIVE);
        String tenantName = active
                .flatMap(l -> tenants.findById(l.getTenantId()))
                .map(Tenant::getFullName)
                .orElse(null);

        return UnitResponse.builder()
                .id(u.getId())
                .propertyId(u.getPropertyId())
                .landlordId(u.getLandlordId())
                .label(u.getLabel())
                .unitType(u.getUnitType())
                .bedrooms(u.getBedrooms())
                .bathrooms(u.getBathrooms())
                .sizeSqm(u.getSizeSqm())
                .rentAmount(u.getRentAmount())
                .depositAmount(u.getDepositAmount())
                .status(u.getStatus().name())
                .notes(u.getNotes())
                .activeLeaseId(active.map(Lease::getId).orElse(null))
                .activeTenantName(tenantName)
                .createdAt(u.getCreatedAt())
                .build();
    }
}
