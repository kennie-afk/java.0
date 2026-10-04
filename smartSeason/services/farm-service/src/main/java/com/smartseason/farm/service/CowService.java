package com.smartseason.farm.service;

import com.smartseason.farm.domain.Cow;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.EventPublisher;
import com.smartseason.farm.platform.ReferenceChecker;
import com.smartseason.farm.platform.Cursor;
import com.smartseason.farm.platform.CursorPage;
import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.platform.ResourceNotFoundException;
import com.smartseason.farm.platform.TenantContext;
import com.smartseason.farm.repo.CowRepository;
import com.smartseason.farm.web.dto.CowCreateRequest;
import com.smartseason.farm.web.dto.CowResponse;
import com.smartseason.farm.web.dto.CowUpdateRequest;
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
public class CowService {

    private static final String RESOURCE = "Cow";
    private static final String ENTITY = "cows";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("farmId", UUID.class),
            Map.entry("tagNo", String.class),
            Map.entry("name", String.class),
            Map.entry("breed", String.class),
            Map.entry("sex", Cow.Sex.class),
            Map.entry("damId", UUID.class),
            Map.entry("sireRef", String.class),
            Map.entry("status", Cow.Status.class));

    private static final List<String> SEARCHABLE = List.of("tagNo", "name", "breed", "sireRef");

    private final CowRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public CowService(CowRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<CowResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<Cow>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(CowResponse::from));
    }

    public PageResponse<CowResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(CowResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<CowResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<Cow> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(CowResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public CowResponse get(UUID id) {
        return CowResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public CowResponse create(CowCreateRequest request) {
        Cow entity = new Cow();
        entity.setTenantId(TenantContext.requireTenantId());

        references.require("Farm", "farmId", request.farmId());
        entity.setFarmId(request.farmId());
        entity.setTagNo(request.tagNo());
        entity.setName(request.name());
        entity.setBreed(request.breed());
        entity.setSex(request.sex());
        entity.setBirthDate(request.birthDate());
        entity.setDamId(request.damId());
        entity.setSireRef(request.sireRef());
        entity.setStatus(request.status());
        entity.setAcquiredOn(request.acquiredOn());
        entity.setExitedOn(request.exitedOn());
        entity.setNotes(request.notes());

        Cow saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("farm", "CowCreated", saved.getId(), CowResponse.from(saved));
        return CowResponse.from(saved);
    }

    @Transactional
    public CowResponse update(UUID id, CowUpdateRequest request) {
        Cow entity = require(id);
        references.require("Farm", "farmId", request.farmId());
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.tagNo() != null) {
            entity.setTagNo(request.tagNo());
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.breed() != null) {
            entity.setBreed(request.breed());
        }
        if (request.sex() != null) {
            entity.setSex(request.sex());
        }
        if (request.birthDate() != null) {
            entity.setBirthDate(request.birthDate());
        }
        if (request.damId() != null) {
            entity.setDamId(request.damId());
        }
        if (request.sireRef() != null) {
            entity.setSireRef(request.sireRef());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.acquiredOn() != null) {
            entity.setAcquiredOn(request.acquiredOn());
        }
        if (request.exitedOn() != null) {
            entity.setExitedOn(request.exitedOn());
        }
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }

        Cow saved = repository.save(entity);
        events.publish("farm", "CowUpdated", saved.getId(), CowResponse.from(saved));
        return CowResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Cow entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "CowDeleted", id, null);
    }

    private Cow require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
