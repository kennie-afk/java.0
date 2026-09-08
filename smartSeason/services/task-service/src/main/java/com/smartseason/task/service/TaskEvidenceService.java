package com.smartseason.task.service;

import com.smartseason.task.domain.TaskEvidence;
import com.smartseason.task.platform.CountCache;
import com.smartseason.task.platform.CountCache;
import com.smartseason.task.platform.EventPublisher;
import com.smartseason.task.platform.PageResponse;
import com.smartseason.task.platform.ResourceNotFoundException;
import com.smartseason.task.platform.TenantContext;
import com.smartseason.task.repo.TaskEvidenceRepository;
import com.smartseason.task.web.dto.TaskEvidenceCreateRequest;
import com.smartseason.task.web.dto.TaskEvidenceResponse;
import com.smartseason.task.web.dto.TaskEvidenceUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskEvidenceService {

    private static final String RESOURCE = "TaskEvidence";
    private static final String ENTITY = "task_evidence";

    private final TaskEvidenceRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public TaskEvidenceService(TaskEvidenceRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<TaskEvidenceResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(TaskEvidenceResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public TaskEvidenceResponse get(UUID id) {
        return TaskEvidenceResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public TaskEvidenceResponse create(TaskEvidenceCreateRequest request) {
        TaskEvidence entity = new TaskEvidence();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setAssignmentId(request.assignmentId());
        entity.setWorkOrderId(request.workOrderId());
        entity.setEvidenceType(request.evidenceType());
        entity.setMediaUrl(request.mediaUrl());
        entity.setPerceptualHash(request.perceptualHash());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setCapturedAt(request.capturedAt());
        entity.setExifTimestamp(request.exifTimestamp());
        entity.setMockLocation(request.mockLocation());
        entity.setNotes(request.notes());
        entity.setVerdict(request.verdict());

        TaskEvidence saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("workforce", "TaskEvidenceCreated", saved.getId(), TaskEvidenceResponse.from(saved));
        return TaskEvidenceResponse.from(saved);
    }

    @Transactional
    public TaskEvidenceResponse update(UUID id, TaskEvidenceUpdateRequest request) {
        TaskEvidence entity = require(id);
        if (request.assignmentId() != null) {
            entity.setAssignmentId(request.assignmentId());
        }
        if (request.workOrderId() != null) {
            entity.setWorkOrderId(request.workOrderId());
        }
        if (request.evidenceType() != null) {
            entity.setEvidenceType(request.evidenceType());
        }
        if (request.mediaUrl() != null) {
            entity.setMediaUrl(request.mediaUrl());
        }
        if (request.perceptualHash() != null) {
            entity.setPerceptualHash(request.perceptualHash());
        }
        if (request.latitude() != null) {
            entity.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            entity.setLongitude(request.longitude());
        }
        if (request.capturedAt() != null) {
            entity.setCapturedAt(request.capturedAt());
        }
        if (request.exifTimestamp() != null) {
            entity.setExifTimestamp(request.exifTimestamp());
        }
        if (request.mockLocation() != null) {
            entity.setMockLocation(request.mockLocation());
        }
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }
        if (request.verdict() != null) {
            entity.setVerdict(request.verdict());
        }

        TaskEvidence saved = repository.save(entity);
        events.publish("workforce", "TaskEvidenceUpdated", saved.getId(), TaskEvidenceResponse.from(saved));
        return TaskEvidenceResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        TaskEvidence entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("workforce", "TaskEvidenceDeleted", id, null);
    }

    private TaskEvidence require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
