package com.smartseason.workforce.service;

import com.smartseason.workforce.domain.WorkerContract;
import com.smartseason.workforce.platform.EventPublisher;
import com.smartseason.workforce.platform.PageResponse;
import com.smartseason.workforce.platform.ResourceNotFoundException;
import com.smartseason.workforce.platform.TenantContext;
import com.smartseason.workforce.repo.WorkerContractRepository;
import com.smartseason.workforce.web.dto.WorkerContractCreateRequest;
import com.smartseason.workforce.web.dto.WorkerContractResponse;
import com.smartseason.workforce.web.dto.WorkerContractUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkerContractService {

    private static final String RESOURCE = "WorkerContract";

    private final WorkerContractRepository repository;
    private final EventPublisher events;

    public WorkerContractService(WorkerContractRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<WorkerContractResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(WorkerContractResponse::from));
    }

    public WorkerContractResponse get(UUID id) {
        return WorkerContractResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public WorkerContractResponse create(WorkerContractCreateRequest request) {
        WorkerContract entity = new WorkerContract();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setWorkerId(request.workerId());
        entity.setFarmId(request.farmId());
        entity.setContractType(request.contractType());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setDailyRate(request.dailyRate());
        entity.setPieceRate(request.pieceRate());
        entity.setPieceUnit(request.pieceUnit());
        entity.setSupervisorId(request.supervisorId());
        entity.setStatus(request.status());
        entity.setTerms(request.terms());

        WorkerContract saved = repository.save(entity);
        events.publish("workforce", "WorkerContractCreated", saved.getId(), WorkerContractResponse.from(saved));
        return WorkerContractResponse.from(saved);
    }

    @Transactional
    public WorkerContractResponse update(UUID id, WorkerContractUpdateRequest request) {
        WorkerContract entity = require(id);
        if (request.workerId() != null) {
            entity.setWorkerId(request.workerId());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.contractType() != null) {
            entity.setContractType(request.contractType());
        }
        if (request.startDate() != null) {
            entity.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            entity.setEndDate(request.endDate());
        }
        if (request.dailyRate() != null) {
            entity.setDailyRate(request.dailyRate());
        }
        if (request.pieceRate() != null) {
            entity.setPieceRate(request.pieceRate());
        }
        if (request.pieceUnit() != null) {
            entity.setPieceUnit(request.pieceUnit());
        }
        if (request.supervisorId() != null) {
            entity.setSupervisorId(request.supervisorId());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.terms() != null) {
            entity.setTerms(request.terms());
        }

        WorkerContract saved = repository.save(entity);
        events.publish("workforce", "WorkerContractUpdated", saved.getId(), WorkerContractResponse.from(saved));
        return WorkerContractResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        WorkerContract entity = require(id);
        repository.delete(entity);
        events.publish("workforce", "WorkerContractDeleted", id, null);
    }

    private WorkerContract require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
