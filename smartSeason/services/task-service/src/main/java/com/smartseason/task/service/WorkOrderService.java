package com.smartseason.task.service;

import com.smartseason.task.domain.WorkOrder;
import com.smartseason.task.platform.CountCache;
import com.smartseason.task.platform.CountCache;
import com.smartseason.task.platform.EventPublisher;
import com.smartseason.task.platform.Cursor;
import com.smartseason.task.platform.CursorPage;
import com.smartseason.task.platform.PageResponse;
import com.smartseason.task.platform.ResourceNotFoundException;
import com.smartseason.task.platform.TenantContext;
import com.smartseason.task.repo.WorkOrderRepository;
import com.smartseason.task.web.dto.WorkOrderCreateRequest;
import com.smartseason.task.web.dto.WorkOrderResponse;
import com.smartseason.task.web.dto.WorkOrderUpdateRequest;
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
public class WorkOrderService {

    private static final String RESOURCE = "WorkOrder";
    private static final String ENTITY = "work_orders";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("farmId", UUID.class),
            Map.entry("plotId", UUID.class),
            Map.entry("seasonId", UUID.class),
            Map.entry("taskCode", String.class),
            Map.entry("title", String.class),
            Map.entry("priority", WorkOrder.Priority.class),
            Map.entry("createdBy", UUID.class),
            Map.entry("status", WorkOrder.Status.class));

    private static final List<String> SEARCHABLE = List.of("taskCode", "title");

    private final WorkOrderRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public WorkOrderService(WorkOrderRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<WorkOrderResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<WorkOrder>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(WorkOrderResponse::from));
    }

    public PageResponse<WorkOrderResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(WorkOrderResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<WorkOrderResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<WorkOrder> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(WorkOrderResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public WorkOrderResponse get(UUID id) {
        return WorkOrderResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public WorkOrderResponse create(WorkOrderCreateRequest request) {
        WorkOrder entity = new WorkOrder();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setFarmId(request.farmId());
        entity.setPlotId(request.plotId());
        entity.setSeasonId(request.seasonId());
        entity.setTaskCode(request.taskCode());
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setDueDate(request.dueDate());
        entity.setPriority(request.priority());
        entity.setEstimatedHours(request.estimatedHours());
        entity.setCreatedBy(request.createdBy());
        entity.setStatus(request.status());

        WorkOrder saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("workforce", "WorkOrderCreated", saved.getId(), WorkOrderResponse.from(saved));
        return WorkOrderResponse.from(saved);
    }

    @Transactional
    public WorkOrderResponse update(UUID id, WorkOrderUpdateRequest request) {
        WorkOrder entity = require(id);
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.seasonId() != null) {
            entity.setSeasonId(request.seasonId());
        }
        if (request.taskCode() != null) {
            entity.setTaskCode(request.taskCode());
        }
        if (request.title() != null) {
            entity.setTitle(request.title());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.dueDate() != null) {
            entity.setDueDate(request.dueDate());
        }
        if (request.priority() != null) {
            entity.setPriority(request.priority());
        }
        if (request.estimatedHours() != null) {
            entity.setEstimatedHours(request.estimatedHours());
        }
        if (request.createdBy() != null) {
            entity.setCreatedBy(request.createdBy());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        WorkOrder saved = repository.save(entity);
        events.publish("workforce", "WorkOrderUpdated", saved.getId(), WorkOrderResponse.from(saved));
        return WorkOrderResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        WorkOrder entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("workforce", "WorkOrderDeleted", id, null);
    }

    private WorkOrder require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
