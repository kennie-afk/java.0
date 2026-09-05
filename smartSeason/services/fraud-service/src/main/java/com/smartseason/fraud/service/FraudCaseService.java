package com.smartseason.fraud.service;

import com.smartseason.fraud.domain.FraudCase;
import com.smartseason.fraud.platform.EventPublisher;
import com.smartseason.fraud.platform.PageResponse;
import com.smartseason.fraud.platform.ResourceNotFoundException;
import com.smartseason.fraud.platform.TenantContext;
import com.smartseason.fraud.repo.FraudCaseRepository;
import com.smartseason.fraud.web.dto.FraudCaseCreateRequest;
import com.smartseason.fraud.web.dto.FraudCaseResponse;
import com.smartseason.fraud.web.dto.FraudCaseUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FraudCaseService {

    private static final String RESOURCE = "FraudCase";

    private final FraudCaseRepository repository;
    private final EventPublisher events;

    public FraudCaseService(FraudCaseRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<FraudCaseResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(FraudCaseResponse::from));
    }

    public FraudCaseResponse get(UUID id) {
        return FraudCaseResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public FraudCaseResponse create(FraudCaseCreateRequest request) {
        FraudCase entity = new FraudCase();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCaseNumber(request.caseNumber());
        entity.setSubjectType(request.subjectType());
        entity.setSubjectId(request.subjectId());
        entity.setFarmId(request.farmId());
        entity.setTypology(request.typology());
        entity.setSeverity(request.severity());
        entity.setConfidence(request.confidence());
        entity.setOpenedAt(request.openedAt());
        entity.setStatus(request.status());
        entity.setAssignedTo(request.assignedTo());
        entity.setResolvedAt(request.resolvedAt());
        entity.setResolution(request.resolution());
        entity.setPayoutHeld(request.payoutHeld());
        entity.setAppealedAt(request.appealedAt());
        entity.setAppealOutcome(request.appealOutcome());

        FraudCase saved = repository.save(entity);
        events.publish("workforce", "FraudCaseCreated", saved.getId(), FraudCaseResponse.from(saved));
        return FraudCaseResponse.from(saved);
    }

    @Transactional
    public FraudCaseResponse update(UUID id, FraudCaseUpdateRequest request) {
        FraudCase entity = require(id);
        if (request.caseNumber() != null) {
            entity.setCaseNumber(request.caseNumber());
        }
        if (request.subjectType() != null) {
            entity.setSubjectType(request.subjectType());
        }
        if (request.subjectId() != null) {
            entity.setSubjectId(request.subjectId());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.typology() != null) {
            entity.setTypology(request.typology());
        }
        if (request.severity() != null) {
            entity.setSeverity(request.severity());
        }
        if (request.confidence() != null) {
            entity.setConfidence(request.confidence());
        }
        if (request.openedAt() != null) {
            entity.setOpenedAt(request.openedAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.assignedTo() != null) {
            entity.setAssignedTo(request.assignedTo());
        }
        if (request.resolvedAt() != null) {
            entity.setResolvedAt(request.resolvedAt());
        }
        if (request.resolution() != null) {
            entity.setResolution(request.resolution());
        }
        if (request.payoutHeld() != null) {
            entity.setPayoutHeld(request.payoutHeld());
        }
        if (request.appealedAt() != null) {
            entity.setAppealedAt(request.appealedAt());
        }
        if (request.appealOutcome() != null) {
            entity.setAppealOutcome(request.appealOutcome());
        }

        FraudCase saved = repository.save(entity);
        events.publish("workforce", "FraudCaseUpdated", saved.getId(), FraudCaseResponse.from(saved));
        return FraudCaseResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        FraudCase entity = require(id);
        repository.delete(entity);
        events.publish("workforce", "FraudCaseDeleted", id, null);
    }

    private FraudCase require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
