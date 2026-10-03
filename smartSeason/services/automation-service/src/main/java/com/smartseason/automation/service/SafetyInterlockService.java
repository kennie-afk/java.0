package com.smartseason.automation.service;

import com.smartseason.automation.domain.SafetyInterlock;
import com.smartseason.automation.platform.CountCache;
import com.smartseason.automation.platform.CountCache;
import com.smartseason.automation.platform.EventPublisher;
import com.smartseason.automation.platform.ReferenceChecker;
import com.smartseason.automation.platform.Cursor;
import com.smartseason.automation.platform.CursorPage;
import com.smartseason.automation.platform.PageResponse;
import com.smartseason.automation.platform.ResourceNotFoundException;
import com.smartseason.automation.platform.TenantContext;
import com.smartseason.automation.repo.SafetyInterlockRepository;
import com.smartseason.automation.web.dto.SafetyInterlockCreateRequest;
import com.smartseason.automation.web.dto.SafetyInterlockResponse;
import com.smartseason.automation.web.dto.SafetyInterlockUpdateRequest;
import com.smartseason.automation.platform.ListFilter;
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
public class SafetyInterlockService {

    private static final String RESOURCE = "SafetyInterlock";
    private static final String ENTITY = "safety_interlocks";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("deviceId", UUID.class),
            Map.entry("interlockType", SafetyInterlock.InterlockType.class),
            Map.entry("conflictingDeviceId", UUID.class),
            Map.entry("engaged", Boolean.class),
            Map.entry("reason", String.class));

    private static final List<String> SEARCHABLE = List.of("reason");

    private final SafetyInterlockRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public SafetyInterlockService(SafetyInterlockRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<SafetyInterlockResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<SafetyInterlock>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(SafetyInterlockResponse::from));
    }

    public PageResponse<SafetyInterlockResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(SafetyInterlockResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<SafetyInterlockResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<SafetyInterlock> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(SafetyInterlockResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public SafetyInterlockResponse get(UUID id) {
        return SafetyInterlockResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public SafetyInterlockResponse create(SafetyInterlockCreateRequest request) {
        SafetyInterlock entity = new SafetyInterlock();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setDeviceId(request.deviceId());
        entity.setInterlockType(request.interlockType());
        entity.setMaxRuntimeSeconds(request.maxRuntimeSeconds());
        entity.setConflictingDeviceId(request.conflictingDeviceId());
        entity.setEngaged(request.engaged());
        entity.setEngagedAt(request.engagedAt());
        entity.setReason(request.reason());

        SafetyInterlock saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("iot", "SafetyInterlockCreated", saved.getId(), SafetyInterlockResponse.from(saved));
        return SafetyInterlockResponse.from(saved);
    }

    @Transactional
    public SafetyInterlockResponse update(UUID id, SafetyInterlockUpdateRequest request) {
        SafetyInterlock entity = require(id);
        if (request.deviceId() != null) {
            entity.setDeviceId(request.deviceId());
        }
        if (request.interlockType() != null) {
            entity.setInterlockType(request.interlockType());
        }
        if (request.maxRuntimeSeconds() != null) {
            entity.setMaxRuntimeSeconds(request.maxRuntimeSeconds());
        }
        if (request.conflictingDeviceId() != null) {
            entity.setConflictingDeviceId(request.conflictingDeviceId());
        }
        if (request.engaged() != null) {
            entity.setEngaged(request.engaged());
        }
        if (request.engagedAt() != null) {
            entity.setEngagedAt(request.engagedAt());
        }
        if (request.reason() != null) {
            entity.setReason(request.reason());
        }

        SafetyInterlock saved = repository.save(entity);
        events.publish("iot", "SafetyInterlockUpdated", saved.getId(), SafetyInterlockResponse.from(saved));
        return SafetyInterlockResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        SafetyInterlock entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("iot", "SafetyInterlockDeleted", id, null);
    }

    private SafetyInterlock require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
