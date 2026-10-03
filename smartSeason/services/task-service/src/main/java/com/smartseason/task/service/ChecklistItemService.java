package com.smartseason.task.service;

import com.smartseason.task.domain.ChecklistItem;
import com.smartseason.task.platform.CountCache;
import com.smartseason.task.platform.CountCache;
import com.smartseason.task.platform.EventPublisher;
import com.smartseason.task.platform.Cursor;
import com.smartseason.task.platform.CursorPage;
import com.smartseason.task.platform.PageResponse;
import com.smartseason.task.platform.ResourceNotFoundException;
import com.smartseason.task.platform.TenantContext;
import com.smartseason.task.repo.ChecklistItemRepository;
import com.smartseason.task.web.dto.ChecklistItemCreateRequest;
import com.smartseason.task.web.dto.ChecklistItemResponse;
import com.smartseason.task.web.dto.ChecklistItemUpdateRequest;
import com.smartseason.task.platform.ListFilter;
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
public class ChecklistItemService {

    private static final String RESOURCE = "ChecklistItem";
    private static final String ENTITY = "checklist_items";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("workOrderId", UUID.class),
            Map.entry("label", String.class),
            Map.entry("required", Boolean.class),
            Map.entry("completed", Boolean.class),
            Map.entry("completedBy", UUID.class));

    private static final List<String> SEARCHABLE = List.of("label");

    private final ChecklistItemRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public ChecklistItemService(ChecklistItemRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<ChecklistItemResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<ChecklistItem>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(ChecklistItemResponse::from));
    }

    public PageResponse<ChecklistItemResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(ChecklistItemResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<ChecklistItemResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<ChecklistItem> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(ChecklistItemResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public ChecklistItemResponse get(UUID id) {
        return ChecklistItemResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ChecklistItemResponse create(ChecklistItemCreateRequest request) {
        ChecklistItem entity = new ChecklistItem();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setWorkOrderId(request.workOrderId());
        entity.setLabel(request.label());
        entity.setSequence(request.sequence());
        entity.setRequired(request.required());
        entity.setCompleted(request.completed());
        entity.setCompletedAt(request.completedAt());
        entity.setCompletedBy(request.completedBy());

        ChecklistItem saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("workforce", "ChecklistItemCreated", saved.getId(), ChecklistItemResponse.from(saved));
        return ChecklistItemResponse.from(saved);
    }

    @Transactional
    public ChecklistItemResponse update(UUID id, ChecklistItemUpdateRequest request) {
        ChecklistItem entity = require(id);
        if (request.workOrderId() != null) {
            entity.setWorkOrderId(request.workOrderId());
        }
        if (request.label() != null) {
            entity.setLabel(request.label());
        }
        if (request.sequence() != null) {
            entity.setSequence(request.sequence());
        }
        if (request.required() != null) {
            entity.setRequired(request.required());
        }
        if (request.completed() != null) {
            entity.setCompleted(request.completed());
        }
        if (request.completedAt() != null) {
            entity.setCompletedAt(request.completedAt());
        }
        if (request.completedBy() != null) {
            entity.setCompletedBy(request.completedBy());
        }

        ChecklistItem saved = repository.save(entity);
        events.publish("workforce", "ChecklistItemUpdated", saved.getId(), ChecklistItemResponse.from(saved));
        return ChecklistItemResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        ChecklistItem entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("workforce", "ChecklistItemDeleted", id, null);
    }

    private ChecklistItem require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
