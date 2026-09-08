package com.smartseason.logistics.service;

import com.smartseason.logistics.domain.Driver;
import com.smartseason.logistics.platform.CountCache;
import com.smartseason.logistics.platform.CountCache;
import com.smartseason.logistics.platform.EventPublisher;
import com.smartseason.logistics.platform.PageResponse;
import com.smartseason.logistics.platform.ResourceNotFoundException;
import com.smartseason.logistics.platform.TenantContext;
import com.smartseason.logistics.repo.DriverRepository;
import com.smartseason.logistics.web.dto.DriverCreateRequest;
import com.smartseason.logistics.web.dto.DriverResponse;
import com.smartseason.logistics.web.dto.DriverUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DriverService {

    private static final String RESOURCE = "Driver";
    private static final String ENTITY = "drivers";

    private final DriverRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public DriverService(DriverRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<DriverResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(DriverResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public DriverResponse get(UUID id) {
        return DriverResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public DriverResponse create(DriverCreateRequest request) {
        Driver entity = new Driver();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setUserId(request.userId());
        entity.setFullName(request.fullName());
        entity.setPhone(request.phone());
        entity.setLicenceNumber(request.licenceNumber());
        entity.setLicenceExpiry(request.licenceExpiry());
        entity.setAssignedVehicleId(request.assignedVehicleId());
        entity.setRating(request.rating());
        entity.setStatus(request.status());

        Driver saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "DriverCreated", saved.getId(), DriverResponse.from(saved));
        return DriverResponse.from(saved);
    }

    @Transactional
    public DriverResponse update(UUID id, DriverUpdateRequest request) {
        Driver entity = require(id);
        if (request.userId() != null) {
            entity.setUserId(request.userId());
        }
        if (request.fullName() != null) {
            entity.setFullName(request.fullName());
        }
        if (request.phone() != null) {
            entity.setPhone(request.phone());
        }
        if (request.licenceNumber() != null) {
            entity.setLicenceNumber(request.licenceNumber());
        }
        if (request.licenceExpiry() != null) {
            entity.setLicenceExpiry(request.licenceExpiry());
        }
        if (request.assignedVehicleId() != null) {
            entity.setAssignedVehicleId(request.assignedVehicleId());
        }
        if (request.rating() != null) {
            entity.setRating(request.rating());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        Driver saved = repository.save(entity);
        events.publish("market", "DriverUpdated", saved.getId(), DriverResponse.from(saved));
        return DriverResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Driver entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "DriverDeleted", id, null);
    }

    private Driver require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
