package com.smartseason.logistics.service;

import com.smartseason.logistics.domain.Vehicle;
import com.smartseason.logistics.platform.EventPublisher;
import com.smartseason.logistics.platform.PageResponse;
import com.smartseason.logistics.platform.ResourceNotFoundException;
import com.smartseason.logistics.platform.TenantContext;
import com.smartseason.logistics.repo.VehicleRepository;
import com.smartseason.logistics.web.dto.VehicleCreateRequest;
import com.smartseason.logistics.web.dto.VehicleResponse;
import com.smartseason.logistics.web.dto.VehicleUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VehicleService {

    private static final String RESOURCE = "Vehicle";

    private final VehicleRepository repository;
    private final EventPublisher events;

    public VehicleService(VehicleRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<VehicleResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(VehicleResponse::from));
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
        events.publish("market", "VehicleDeleted", id, null);
    }

    private Vehicle require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
