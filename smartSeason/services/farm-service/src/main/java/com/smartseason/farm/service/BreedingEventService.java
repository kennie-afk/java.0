package com.smartseason.farm.service;

import com.smartseason.farm.domain.BreedingEvent;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.EventPublisher;
import com.smartseason.farm.platform.ReferenceChecker;
import com.smartseason.farm.platform.Cursor;
import com.smartseason.farm.platform.CursorPage;
import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.platform.ResourceNotFoundException;
import com.smartseason.farm.platform.TenantContext;
import com.smartseason.farm.repo.BreedingEventRepository;
import com.smartseason.farm.web.dto.BreedingEventCreateRequest;
import com.smartseason.farm.web.dto.BreedingEventResponse;
import com.smartseason.farm.web.dto.BreedingEventUpdateRequest;
import com.smartseason.farm.platform.ListFilter;
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
public class BreedingEventService {

    private static final String RESOURCE = "BreedingEvent";
    private static final String ENTITY = "breeding_events";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("cowId", UUID.class),
            Map.entry("farmId", UUID.class),
            Map.entry("eventType", BreedingEvent.EventType.class),
            Map.entry("method", BreedingEvent.Method.class),
            Map.entry("sireRef", String.class),
            Map.entry("outcome", String.class));

    private static final List<String> SEARCHABLE = List.of("sireRef", "outcome");

    private final BreedingEventRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public BreedingEventService(BreedingEventRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<BreedingEventResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<BreedingEvent>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(BreedingEventResponse::from));
    }

    public PageResponse<BreedingEventResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(BreedingEventResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<BreedingEventResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<BreedingEvent> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(BreedingEventResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public BreedingEventResponse get(UUID id) {
        return BreedingEventResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public BreedingEventResponse create(BreedingEventCreateRequest request) {
        BreedingEvent entity = new BreedingEvent();
        entity.setTenantId(TenantContext.requireTenantId());

        references.require("Cow", "cowId", request.cowId());
        references.require("Farm", "farmId", request.farmId());
        entity.setCowId(request.cowId());
        entity.setFarmId(request.farmId());
        entity.setEventDate(request.eventDate());
        entity.setEventType(request.eventType());
        entity.setMethod(request.method());
        entity.setSireRef(request.sireRef());
        entity.setOutcome(request.outcome());
        entity.setExpectedCalvingOn(request.expectedCalvingOn());
        entity.setNotes(request.notes());

        BreedingEvent saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("farm", "BreedingEventCreated", saved.getId(), BreedingEventResponse.from(saved));
        return BreedingEventResponse.from(saved);
    }

    @Transactional
    public BreedingEventResponse update(UUID id, BreedingEventUpdateRequest request) {
        BreedingEvent entity = require(id);
        references.require("Cow", "cowId", request.cowId());
        references.require("Farm", "farmId", request.farmId());
        if (request.cowId() != null) {
            entity.setCowId(request.cowId());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.eventDate() != null) {
            entity.setEventDate(request.eventDate());
        }
        if (request.eventType() != null) {
            entity.setEventType(request.eventType());
        }
        if (request.method() != null) {
            entity.setMethod(request.method());
        }
        if (request.sireRef() != null) {
            entity.setSireRef(request.sireRef());
        }
        if (request.outcome() != null) {
            entity.setOutcome(request.outcome());
        }
        if (request.expectedCalvingOn() != null) {
            entity.setExpectedCalvingOn(request.expectedCalvingOn());
        }
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }

        BreedingEvent saved = repository.save(entity);
        events.publish("farm", "BreedingEventUpdated", saved.getId(), BreedingEventResponse.from(saved));
        return BreedingEventResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        BreedingEvent entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "BreedingEventDeleted", id, null);
    }

    private BreedingEvent require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
