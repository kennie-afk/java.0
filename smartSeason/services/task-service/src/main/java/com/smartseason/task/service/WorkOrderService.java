package com.smartseason.task.service;

import com.smartseason.task.domain.WorkOrder;
import com.smartseason.task.platform.EventPublisher;
import com.smartseason.task.platform.PageResponse;
import com.smartseason.task.platform.ResourceNotFoundException;
import com.smartseason.task.platform.TenantContext;
import com.smartseason.task.repo.WorkOrderRepository;
import com.smartseason.task.web.dto.WorkOrderCreateRequest;
import com.smartseason.task.web.dto.WorkOrderResponse;
import com.smartseason.task.web.dto.WorkOrderUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkOrderService {

    private static final String RESOURCE = "WorkOrder";

    private final WorkOrderRepository repository;
    private final EventPublisher events;

    public WorkOrderService(WorkOrderRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<WorkOrderResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(WorkOrderResponse::from));
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
        events.publish("workforce", "WorkOrderDeleted", id, null);
    }

    private WorkOrder require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
