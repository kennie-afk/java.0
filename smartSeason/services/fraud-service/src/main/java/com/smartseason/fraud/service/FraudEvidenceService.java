package com.smartseason.fraud.service;

import com.smartseason.fraud.domain.FraudEvidence;
import com.smartseason.fraud.platform.EventPublisher;
import com.smartseason.fraud.platform.PageResponse;
import com.smartseason.fraud.platform.ResourceNotFoundException;
import com.smartseason.fraud.platform.TenantContext;
import com.smartseason.fraud.repo.FraudEvidenceRepository;
import com.smartseason.fraud.web.dto.FraudEvidenceCreateRequest;
import com.smartseason.fraud.web.dto.FraudEvidenceResponse;
import com.smartseason.fraud.web.dto.FraudEvidenceUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FraudEvidenceService {

    private static final String RESOURCE = "FraudEvidence";

    private final FraudEvidenceRepository repository;
    private final EventPublisher events;

    public FraudEvidenceService(FraudEvidenceRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<FraudEvidenceResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(FraudEvidenceResponse::from));
    }

    public FraudEvidenceResponse get(UUID id) {
        return FraudEvidenceResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public FraudEvidenceResponse create(FraudEvidenceCreateRequest request) {
        FraudEvidence entity = new FraudEvidence();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCaseId(request.caseId());
        entity.setLabel(request.label());
        entity.setEvidenceType(request.evidenceType());
        entity.setPayload(request.payload());
        entity.setWeight(request.weight());
        entity.setCollectedAt(request.collectedAt());

        FraudEvidence saved = repository.save(entity);
        events.publish("workforce", "FraudEvidenceCreated", saved.getId(), FraudEvidenceResponse.from(saved));
        return FraudEvidenceResponse.from(saved);
    }

    @Transactional
    public FraudEvidenceResponse update(UUID id, FraudEvidenceUpdateRequest request) {
        FraudEvidence entity = require(id);
        if (request.caseId() != null) {
            entity.setCaseId(request.caseId());
        }
        if (request.label() != null) {
            entity.setLabel(request.label());
        }
        if (request.evidenceType() != null) {
            entity.setEvidenceType(request.evidenceType());
        }
        if (request.payload() != null) {
            entity.setPayload(request.payload());
        }
        if (request.weight() != null) {
            entity.setWeight(request.weight());
        }
        if (request.collectedAt() != null) {
            entity.setCollectedAt(request.collectedAt());
        }

        FraudEvidence saved = repository.save(entity);
        events.publish("workforce", "FraudEvidenceUpdated", saved.getId(), FraudEvidenceResponse.from(saved));
        return FraudEvidenceResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        FraudEvidence entity = require(id);
        repository.delete(entity);
        events.publish("workforce", "FraudEvidenceDeleted", id, null);
    }

    private FraudEvidence require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
