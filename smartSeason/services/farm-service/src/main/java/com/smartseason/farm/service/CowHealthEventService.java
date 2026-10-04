package com.smartseason.farm.service;

import com.smartseason.farm.domain.CowHealthEvent;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.EventPublisher;
import com.smartseason.farm.platform.ReferenceChecker;
import com.smartseason.farm.platform.Cursor;
import com.smartseason.farm.platform.CursorPage;
import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.platform.ResourceNotFoundException;
import com.smartseason.farm.platform.TenantContext;
import com.smartseason.farm.repo.CowHealthEventRepository;
import com.smartseason.farm.web.dto.CowHealthEventCreateRequest;
import com.smartseason.farm.web.dto.CowHealthEventResponse;
import com.smartseason.farm.web.dto.CowHealthEventUpdateRequest;
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
public class CowHealthEventService {

    private static final String RESOURCE = "CowHealthEvent";
    private static final String ENTITY = "cow_health_events";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("cowId", UUID.class),
            Map.entry("farmId", UUID.class),
            Map.entry("eventType", CowHealthEvent.EventType.class),
            Map.entry("medicine", String.class),
            Map.entry("vetName", String.class));

    private static final List<String> SEARCHABLE = List.of("medicine", "vetName");

    private final CowHealthEventRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public CowHealthEventService(CowHealthEventRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<CowHealthEventResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<CowHealthEvent>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(CowHealthEventResponse::from));
    }

    public PageResponse<CowHealthEventResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(CowHealthEventResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<CowHealthEventResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<CowHealthEvent> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(CowHealthEventResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public CowHealthEventResponse get(UUID id) {
        return CowHealthEventResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public CowHealthEventResponse create(CowHealthEventCreateRequest request) {
        CowHealthEvent entity = new CowHealthEvent();
        entity.setTenantId(TenantContext.requireTenantId());

        references.require("Cow", "cowId", request.cowId());
        references.require("Farm", "farmId", request.farmId());
        entity.setCowId(request.cowId());
        entity.setFarmId(request.farmId());
        entity.setEventDate(request.eventDate());
        entity.setEventType(request.eventType());
        entity.setDescription(request.description());
        entity.setMedicine(request.medicine());
        entity.setWithdrawalEndsOn(request.withdrawalEndsOn());
        entity.setVetName(request.vetName());
        entity.setCostAmount(request.costAmount());

        CowHealthEvent saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("farm", "CowHealthEventCreated", saved.getId(), CowHealthEventResponse.from(saved));
        return CowHealthEventResponse.from(saved);
    }

    @Transactional
    public CowHealthEventResponse update(UUID id, CowHealthEventUpdateRequest request) {
        CowHealthEvent entity = require(id);
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
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.medicine() != null) {
            entity.setMedicine(request.medicine());
        }
        if (request.withdrawalEndsOn() != null) {
            entity.setWithdrawalEndsOn(request.withdrawalEndsOn());
        }
        if (request.vetName() != null) {
            entity.setVetName(request.vetName());
        }
        if (request.costAmount() != null) {
            entity.setCostAmount(request.costAmount());
        }

        CowHealthEvent saved = repository.save(entity);
        events.publish("farm", "CowHealthEventUpdated", saved.getId(), CowHealthEventResponse.from(saved));
        return CowHealthEventResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        CowHealthEvent entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "CowHealthEventDeleted", id, null);
    }

    private CowHealthEvent require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
