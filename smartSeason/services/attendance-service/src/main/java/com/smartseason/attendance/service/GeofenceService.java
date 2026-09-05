package com.smartseason.attendance.service;

import com.smartseason.attendance.domain.Geofence;
import com.smartseason.attendance.platform.EventPublisher;
import com.smartseason.attendance.platform.PageResponse;
import com.smartseason.attendance.platform.ResourceNotFoundException;
import com.smartseason.attendance.platform.TenantContext;
import com.smartseason.attendance.repo.GeofenceRepository;
import com.smartseason.attendance.web.dto.GeofenceCreateRequest;
import com.smartseason.attendance.web.dto.GeofenceResponse;
import com.smartseason.attendance.web.dto.GeofenceUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GeofenceService {

    private static final String RESOURCE = "Geofence";

    private final GeofenceRepository repository;
    private final EventPublisher events;

    public GeofenceService(GeofenceRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<GeofenceResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(GeofenceResponse::from));
    }

    public GeofenceResponse get(UUID id) {
        return GeofenceResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public GeofenceResponse create(GeofenceCreateRequest request) {
        Geofence entity = new Geofence();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setFarmId(request.farmId());
        entity.setPlotId(request.plotId());
        entity.setName(request.name());
        entity.setCenterLat(request.centerLat());
        entity.setCenterLng(request.centerLng());
        entity.setRadiusM(request.radiusM());
        entity.setActive(request.active());

        Geofence saved = repository.save(entity);
        events.publish("workforce", "GeofenceCreated", saved.getId(), GeofenceResponse.from(saved));
        return GeofenceResponse.from(saved);
    }

    @Transactional
    public GeofenceResponse update(UUID id, GeofenceUpdateRequest request) {
        Geofence entity = require(id);
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.centerLat() != null) {
            entity.setCenterLat(request.centerLat());
        }
        if (request.centerLng() != null) {
            entity.setCenterLng(request.centerLng());
        }
        if (request.radiusM() != null) {
            entity.setRadiusM(request.radiusM());
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }

        Geofence saved = repository.save(entity);
        events.publish("workforce", "GeofenceUpdated", saved.getId(), GeofenceResponse.from(saved));
        return GeofenceResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Geofence entity = require(id);
        repository.delete(entity);
        events.publish("workforce", "GeofenceDeleted", id, null);
    }

    private Geofence require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
