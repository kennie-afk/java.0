package com.smartseason.logistics.service;

import com.smartseason.logistics.domain.RouteStop;
import com.smartseason.logistics.platform.EventPublisher;
import com.smartseason.logistics.platform.PageResponse;
import com.smartseason.logistics.platform.ResourceNotFoundException;
import com.smartseason.logistics.platform.TenantContext;
import com.smartseason.logistics.repo.RouteStopRepository;
import com.smartseason.logistics.web.dto.RouteStopCreateRequest;
import com.smartseason.logistics.web.dto.RouteStopResponse;
import com.smartseason.logistics.web.dto.RouteStopUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RouteStopService {

    private static final String RESOURCE = "RouteStop";

    private final RouteStopRepository repository;
    private final EventPublisher events;

    public RouteStopService(RouteStopRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<RouteStopResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(RouteStopResponse::from));
    }

    public RouteStopResponse get(UUID id) {
        return RouteStopResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public RouteStopResponse create(RouteStopCreateRequest request) {
        RouteStop entity = new RouteStop();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setTransportJobId(request.transportJobId());
        entity.setSequence(request.sequence());
        entity.setStopType(request.stopType());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setPlannedAt(request.plannedAt());
        entity.setArrivedAt(request.arrivedAt());
        entity.setDepartedAt(request.departedAt());
        entity.setNotes(request.notes());
        entity.setOffRoute(request.offRoute());

        RouteStop saved = repository.save(entity);
        events.publish("market", "RouteStopCreated", saved.getId(), RouteStopResponse.from(saved));
        return RouteStopResponse.from(saved);
    }

    @Transactional
    public RouteStopResponse update(UUID id, RouteStopUpdateRequest request) {
        RouteStop entity = require(id);
        if (request.transportJobId() != null) {
            entity.setTransportJobId(request.transportJobId());
        }
        if (request.sequence() != null) {
            entity.setSequence(request.sequence());
        }
        if (request.stopType() != null) {
            entity.setStopType(request.stopType());
        }
        if (request.latitude() != null) {
            entity.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            entity.setLongitude(request.longitude());
        }
        if (request.plannedAt() != null) {
            entity.setPlannedAt(request.plannedAt());
        }
        if (request.arrivedAt() != null) {
            entity.setArrivedAt(request.arrivedAt());
        }
        if (request.departedAt() != null) {
            entity.setDepartedAt(request.departedAt());
        }
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }
        if (request.offRoute() != null) {
            entity.setOffRoute(request.offRoute());
        }

        RouteStop saved = repository.save(entity);
        events.publish("market", "RouteStopUpdated", saved.getId(), RouteStopResponse.from(saved));
        return RouteStopResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        RouteStop entity = require(id);
        repository.delete(entity);
        events.publish("market", "RouteStopDeleted", id, null);
    }

    private RouteStop require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
