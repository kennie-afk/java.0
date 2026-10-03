package com.smartseason.logistics.service;

import com.smartseason.logistics.domain.RouteStop;
import com.smartseason.logistics.platform.CountCache;
import com.smartseason.logistics.platform.CountCache;
import com.smartseason.logistics.platform.EventPublisher;
import com.smartseason.logistics.platform.ReferenceChecker;
import com.smartseason.logistics.platform.Cursor;
import com.smartseason.logistics.platform.CursorPage;
import com.smartseason.logistics.platform.PageResponse;
import com.smartseason.logistics.platform.ResourceNotFoundException;
import com.smartseason.logistics.platform.TenantContext;
import com.smartseason.logistics.repo.RouteStopRepository;
import com.smartseason.logistics.web.dto.RouteStopCreateRequest;
import com.smartseason.logistics.web.dto.RouteStopResponse;
import com.smartseason.logistics.web.dto.RouteStopUpdateRequest;
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
public class RouteStopService {

    private static final String RESOURCE = "RouteStop";
    private static final String ENTITY = "route_stops";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("transportJobId", UUID.class),
            Map.entry("stopType", RouteStop.StopType.class),
            Map.entry("offRoute", Boolean.class));

    private static final List<String> SEARCHABLE = List.of();

    private final RouteStopRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public RouteStopService(RouteStopRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<RouteStopResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<RouteStop>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(RouteStopResponse::from));
    }

    public PageResponse<RouteStopResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(RouteStopResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<RouteStopResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<RouteStop> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(RouteStopResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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

        references.require("TransportJob", "transportJobId", request.transportJobId());
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
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "RouteStopCreated", saved.getId(), RouteStopResponse.from(saved));
        return RouteStopResponse.from(saved);
    }

    @Transactional
    public RouteStopResponse update(UUID id, RouteStopUpdateRequest request) {
        RouteStop entity = require(id);
        references.require("TransportJob", "transportJobId", request.transportJobId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "RouteStopDeleted", id, null);
    }

    private RouteStop require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
