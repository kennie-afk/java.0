package com.smartseason.logistics.service;

import com.smartseason.logistics.domain.Vehicle;
import com.smartseason.logistics.platform.CountCache;
import com.smartseason.logistics.platform.CountCache;
import com.smartseason.logistics.platform.EventPublisher;
import com.smartseason.logistics.platform.Cursor;
import com.smartseason.logistics.platform.CursorPage;
import com.smartseason.logistics.platform.PageResponse;
import com.smartseason.logistics.platform.ResourceNotFoundException;
import com.smartseason.logistics.platform.TenantContext;
import com.smartseason.logistics.repo.VehicleRepository;
import com.smartseason.logistics.web.dto.VehicleCreateRequest;
import com.smartseason.logistics.web.dto.VehicleResponse;
import com.smartseason.logistics.web.dto.VehicleUpdateRequest;
import com.smartseason.logistics.platform.ListFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VehicleService {

    private static final String RESOURCE = "Vehicle";
    private static final String ENTITY = "vehicles";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("registrationNo", String.class),
            Map.entry("vehicleType", Vehicle.VehicleType.class),
            Map.entry("coldChain", Boolean.class),
            Map.entry("ownerOrgId", UUID.class),
            Map.entry("status", Vehicle.Status.class));

    private static final List<String> SEARCHABLE = List.of("registrationNo");

    private final VehicleRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public VehicleService(VehicleRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<VehicleResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<Vehicle>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(VehicleResponse::from));
    }

    public PageResponse<VehicleResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(VehicleResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<VehicleResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<Vehicle> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(VehicleResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public VehicleResponse get(UUID id) {
        return VehicleResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public VehicleResponse create(VehicleCreateRequest request) {
        Vehicle entity = new Vehicle();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setRegistrationNo(request.registrationNo());
        entity.setVehicleType(request.vehicleType());
        entity.setCapacityKg(request.capacityKg());
        entity.setColdChain(request.coldChain());
        entity.setOwnerOrgId(request.ownerOrgId());
        entity.setOdometerKm(request.odometerKm());
        entity.setStatus(request.status());
        entity.setLastServiceAt(request.lastServiceAt());

        Vehicle saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "VehicleCreated", saved.getId(), VehicleResponse.from(saved));
        return VehicleResponse.from(saved);
    }

    @Transactional
    public VehicleResponse update(UUID id, VehicleUpdateRequest request) {
        Vehicle entity = require(id);
        if (request.registrationNo() != null) {
            entity.setRegistrationNo(request.registrationNo());
        }
        if (request.vehicleType() != null) {
            entity.setVehicleType(request.vehicleType());
        }
        if (request.capacityKg() != null) {
            entity.setCapacityKg(request.capacityKg());
        }
        if (request.coldChain() != null) {
            entity.setColdChain(request.coldChain());
        }
        if (request.ownerOrgId() != null) {
            entity.setOwnerOrgId(request.ownerOrgId());
        }
        if (request.odometerKm() != null) {
            entity.setOdometerKm(request.odometerKm());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.lastServiceAt() != null) {
            entity.setLastServiceAt(request.lastServiceAt());
        }

        Vehicle saved = repository.save(entity);
        events.publish("market", "VehicleUpdated", saved.getId(), VehicleResponse.from(saved));
        return VehicleResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Vehicle entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "VehicleDeleted", id, null);
    }

    private Vehicle require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
