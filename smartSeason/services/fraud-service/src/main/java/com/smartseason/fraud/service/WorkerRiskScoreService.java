package com.smartseason.fraud.service;

import com.smartseason.fraud.domain.WorkerRiskScore;
import com.smartseason.fraud.platform.EventPublisher;
import com.smartseason.fraud.platform.PageResponse;
import com.smartseason.fraud.platform.ResourceNotFoundException;
import com.smartseason.fraud.platform.TenantContext;
import com.smartseason.fraud.repo.WorkerRiskScoreRepository;
import com.smartseason.fraud.web.dto.WorkerRiskScoreCreateRequest;
import com.smartseason.fraud.web.dto.WorkerRiskScoreResponse;
import com.smartseason.fraud.web.dto.WorkerRiskScoreUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkerRiskScoreService {

    private static final String RESOURCE = "WorkerRiskScore";

    private final WorkerRiskScoreRepository repository;
    private final EventPublisher events;

    public WorkerRiskScoreService(WorkerRiskScoreRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<WorkerRiskScoreResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(WorkerRiskScoreResponse::from));
    }

    public WorkerRiskScoreResponse get(UUID id) {
        return WorkerRiskScoreResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public WorkerRiskScoreResponse create(WorkerRiskScoreCreateRequest request) {
        WorkerRiskScore entity = new WorkerRiskScore();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setWorkerId(request.workerId());
        entity.setFarmId(request.farmId());
        entity.setScore(request.score());
        entity.setBand(request.band());
        entity.setOpenCases(request.openCases());
        entity.setLastSignalAt(request.lastSignalAt());
        entity.setDecayAppliedAt(request.decayAppliedAt());
        entity.setComponents(request.components());

        WorkerRiskScore saved = repository.save(entity);
        events.publish("workforce", "WorkerRiskScoreCreated", saved.getId(), WorkerRiskScoreResponse.from(saved));
        return WorkerRiskScoreResponse.from(saved);
    }

    @Transactional
    public WorkerRiskScoreResponse update(UUID id, WorkerRiskScoreUpdateRequest request) {
        WorkerRiskScore entity = require(id);
        if (request.workerId() != null) {
            entity.setWorkerId(request.workerId());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.score() != null) {
            entity.setScore(request.score());
        }
        if (request.band() != null) {
            entity.setBand(request.band());
        }
        if (request.openCases() != null) {
            entity.setOpenCases(request.openCases());
        }
        if (request.lastSignalAt() != null) {
            entity.setLastSignalAt(request.lastSignalAt());
        }
        if (request.decayAppliedAt() != null) {
            entity.setDecayAppliedAt(request.decayAppliedAt());
        }
        if (request.components() != null) {
            entity.setComponents(request.components());
        }

        WorkerRiskScore saved = repository.save(entity);
        events.publish("workforce", "WorkerRiskScoreUpdated", saved.getId(), WorkerRiskScoreResponse.from(saved));
        return WorkerRiskScoreResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        WorkerRiskScore entity = require(id);
        repository.delete(entity);
        events.publish("workforce", "WorkerRiskScoreDeleted", id, null);
    }

    private WorkerRiskScore require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
