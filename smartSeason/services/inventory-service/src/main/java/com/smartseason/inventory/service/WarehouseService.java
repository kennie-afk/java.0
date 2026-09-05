package com.smartseason.inventory.service;

import com.smartseason.inventory.domain.Warehouse;
import com.smartseason.inventory.platform.EventPublisher;
import com.smartseason.inventory.platform.PageResponse;
import com.smartseason.inventory.platform.ResourceNotFoundException;
import com.smartseason.inventory.platform.TenantContext;
import com.smartseason.inventory.repo.WarehouseRepository;
import com.smartseason.inventory.web.dto.WarehouseCreateRequest;
import com.smartseason.inventory.web.dto.WarehouseResponse;
import com.smartseason.inventory.web.dto.WarehouseUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WarehouseService {

    private static final String RESOURCE = "Warehouse";

    private final WarehouseRepository repository;
    private final EventPublisher events;

    public WarehouseService(WarehouseRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<WarehouseResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(WarehouseResponse::from));
    }

    public WarehouseResponse get(UUID id) {
        return WarehouseResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public WarehouseResponse create(WarehouseCreateRequest request) {
        Warehouse entity = new Warehouse();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setName(request.name());
        entity.setCounty(request.county());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setCapacityKg(request.capacityKg());
        entity.setColdChain(request.coldChain());
        entity.setManagerUserId(request.managerUserId());
        entity.setStatus(request.status());

        Warehouse saved = repository.save(entity);
        events.publish("market", "WarehouseCreated", saved.getId(), WarehouseResponse.from(saved));
        return WarehouseResponse.from(saved);
    }

    @Transactional
    public WarehouseResponse update(UUID id, WarehouseUpdateRequest request) {
        Warehouse entity = require(id);
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.county() != null) {
            entity.setCounty(request.county());
        }
        if (request.latitude() != null) {
            entity.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            entity.setLongitude(request.longitude());
        }
        if (request.capacityKg() != null) {
            entity.setCapacityKg(request.capacityKg());
        }
        if (request.coldChain() != null) {
            entity.setColdChain(request.coldChain());
        }
        if (request.managerUserId() != null) {
            entity.setManagerUserId(request.managerUserId());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        Warehouse saved = repository.save(entity);
        events.publish("market", "WarehouseUpdated", saved.getId(), WarehouseResponse.from(saved));
        return WarehouseResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Warehouse entity = require(id);
        repository.delete(entity);
        events.publish("market", "WarehouseDeleted", id, null);
    }

    private Warehouse require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
