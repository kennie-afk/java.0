package com.smartseason.task.service;

import com.smartseason.task.domain.TaskAssignment;
import com.smartseason.task.platform.CountCache;
import com.smartseason.task.platform.CountCache;
import com.smartseason.task.platform.EventPublisher;
import com.smartseason.task.platform.ReferenceChecker;
import com.smartseason.task.platform.Cursor;
import com.smartseason.task.platform.CursorPage;
import com.smartseason.task.platform.PageResponse;
import com.smartseason.task.platform.ResourceNotFoundException;
import com.smartseason.task.platform.TenantContext;
import com.smartseason.task.repo.TaskAssignmentRepository;
import com.smartseason.task.web.dto.TaskAssignmentCreateRequest;
import com.smartseason.task.web.dto.TaskAssignmentResponse;
import com.smartseason.task.web.dto.TaskAssignmentUpdateRequest;
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
public class TaskAssignmentService {

    private static final String RESOURCE = "TaskAssignment";
    private static final String ENTITY = "task_assignments";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("workOrderId", UUID.class),
            Map.entry("workerId", UUID.class),
            Map.entry("workerUserId", UUID.class),
            Map.entry("gangId", UUID.class),
            Map.entry("assignedBy", UUID.class),
            Map.entry("status", TaskAssignment.Status.class));

    private static final List<String> SEARCHABLE = List.of();

    private final TaskAssignmentRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public TaskAssignmentService(TaskAssignmentRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<TaskAssignmentResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<TaskAssignment>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(TaskAssignmentResponse::from));
    }

    public PageResponse<TaskAssignmentResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(TaskAssignmentResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<TaskAssignmentResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<TaskAssignment> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(TaskAssignmentResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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

        references.require("WorkOrder", "workOrderId", request.workOrderId());
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
        references.require("WorkOrder", "workOrderId", request.workOrderId());
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
