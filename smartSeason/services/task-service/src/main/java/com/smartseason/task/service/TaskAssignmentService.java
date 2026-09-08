package com.smartseason.task.service;

import com.smartseason.task.domain.TaskAssignment;
import com.smartseason.task.platform.CountCache;
import com.smartseason.task.platform.CountCache;
import com.smartseason.task.platform.EventPublisher;
import com.smartseason.task.platform.PageResponse;
import com.smartseason.task.platform.ResourceNotFoundException;
import com.smartseason.task.platform.TenantContext;
import com.smartseason.task.repo.TaskAssignmentRepository;
import com.smartseason.task.web.dto.TaskAssignmentCreateRequest;
import com.smartseason.task.web.dto.TaskAssignmentResponse;
import com.smartseason.task.web.dto.TaskAssignmentUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskAssignmentService {

    private static final String RESOURCE = "TaskAssignment";
    private static final String ENTITY = "task_assignments";

    private final TaskAssignmentRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public TaskAssignmentService(TaskAssignmentRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<TaskAssignmentResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(TaskAssignmentResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public TaskAssignmentResponse get(UUID id) {
        return TaskAssignmentResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public TaskAssignmentResponse create(TaskAssignmentCreateRequest request) {
        TaskAssignment entity = new TaskAssignment();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setWorkOrderId(request.workOrderId());
        entity.setWorkerId(request.workerId());
        entity.setWorkerUserId(request.workerUserId());
        entity.setGangId(request.gangId());
        entity.setAssignedBy(request.assignedBy());
        entity.setAssignedAt(request.assignedAt());
        entity.setAcceptedAt(request.acceptedAt());
        entity.setStartedAt(request.startedAt());
        entity.setCompletedAt(request.completedAt());
        entity.setStatus(request.status());

        TaskAssignment saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("workforce", "TaskAssignmentCreated", saved.getId(), TaskAssignmentResponse.from(saved));
        return TaskAssignmentResponse.from(saved);
    }

    @Transactional
    public TaskAssignmentResponse update(UUID id, TaskAssignmentUpdateRequest request) {
        TaskAssignment entity = require(id);
        if (request.workOrderId() != null) {
            entity.setWorkOrderId(request.workOrderId());
        }
        if (request.workerId() != null) {
            entity.setWorkerId(request.workerId());
        }
        if (request.workerUserId() != null) {
            entity.setWorkerUserId(request.workerUserId());
        }
        if (request.gangId() != null) {
            entity.setGangId(request.gangId());
        }
        if (request.assignedBy() != null) {
            entity.setAssignedBy(request.assignedBy());
        }
        if (request.assignedAt() != null) {
            entity.setAssignedAt(request.assignedAt());
        }
        if (request.acceptedAt() != null) {
            entity.setAcceptedAt(request.acceptedAt());
        }
        if (request.startedAt() != null) {
            entity.setStartedAt(request.startedAt());
        }
        if (request.completedAt() != null) {
            entity.setCompletedAt(request.completedAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        TaskAssignment saved = repository.save(entity);
        events.publish("workforce", "TaskAssignmentUpdated", saved.getId(), TaskAssignmentResponse.from(saved));
        return TaskAssignmentResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        TaskAssignment entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("workforce", "TaskAssignmentDeleted", id, null);
    }

    private TaskAssignment require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
