package com.smartseason.farm.service;

import com.smartseason.farm.domain.MilkYield;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.EventPublisher;
import com.smartseason.farm.platform.ReferenceChecker;
import com.smartseason.farm.platform.Cursor;
import com.smartseason.farm.platform.CursorPage;
import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.platform.ResourceNotFoundException;
import com.smartseason.farm.platform.TenantContext;
import com.smartseason.farm.repo.MilkYieldRepository;
import com.smartseason.farm.web.dto.MilkYieldCreateRequest;
import com.smartseason.farm.web.dto.MilkYieldResponse;
import com.smartseason.farm.web.dto.MilkYieldUpdateRequest;
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
public class MilkYieldService {

    private static final String RESOURCE = "MilkYield";
    private static final String ENTITY = "milk_yields";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("cowId", UUID.class),
            Map.entry("farmId", UUID.class),
            Map.entry("session", MilkYield.Session.class),
            Map.entry("recordedBy", UUID.class),
            Map.entry("notes", String.class));

    private static final List<String> SEARCHABLE = List.of("notes");

    private final MilkYieldRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public MilkYieldService(MilkYieldRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<MilkYieldResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<MilkYield>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(MilkYieldResponse::from));
    }

    public PageResponse<MilkYieldResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(MilkYieldResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<MilkYieldResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<MilkYield> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(MilkYieldResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public MilkYieldResponse get(UUID id) {
        return MilkYieldResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public MilkYieldResponse create(MilkYieldCreateRequest request) {
        MilkYield entity = new MilkYield();
        entity.setTenantId(TenantContext.requireTenantId());

        references.require("Cow", "cowId", request.cowId());
        references.require("Farm", "farmId", request.farmId());
        entity.setCowId(request.cowId());
        entity.setFarmId(request.farmId());
        entity.setRecordedOn(request.recordedOn());
        entity.setSession(request.session());
        entity.setLitres(request.litres());
        entity.setRecordedBy(request.recordedBy());
        entity.setNotes(request.notes());

        MilkYield saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("farm", "MilkYieldCreated", saved.getId(), MilkYieldResponse.from(saved));
        return MilkYieldResponse.from(saved);
    }

    @Transactional
    public MilkYieldResponse update(UUID id, MilkYieldUpdateRequest request) {
        MilkYield entity = require(id);
        references.require("Cow", "cowId", request.cowId());
        references.require("Farm", "farmId", request.farmId());
        if (request.cowId() != null) {
            entity.setCowId(request.cowId());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.recordedOn() != null) {
            entity.setRecordedOn(request.recordedOn());
        }
        if (request.session() != null) {
            entity.setSession(request.session());
        }
        if (request.litres() != null) {
            entity.setLitres(request.litres());
        }
        if (request.recordedBy() != null) {
            entity.setRecordedBy(request.recordedBy());
        }
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }

        MilkYield saved = repository.save(entity);
        events.publish("farm", "MilkYieldUpdated", saved.getId(), MilkYieldResponse.from(saved));
        return MilkYieldResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        MilkYield entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "MilkYieldDeleted", id, null);
    }

    private MilkYield require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
