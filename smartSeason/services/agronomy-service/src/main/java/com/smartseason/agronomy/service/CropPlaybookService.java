package com.smartseason.agronomy.service;

import com.smartseason.agronomy.domain.CropPlaybook;
import com.smartseason.agronomy.platform.EventPublisher;
import com.smartseason.agronomy.platform.PageResponse;
import com.smartseason.agronomy.platform.ResourceNotFoundException;
import com.smartseason.agronomy.platform.TenantContext;
import com.smartseason.agronomy.repo.CropPlaybookRepository;
import com.smartseason.agronomy.web.dto.CropPlaybookCreateRequest;
import com.smartseason.agronomy.web.dto.CropPlaybookResponse;
import com.smartseason.agronomy.web.dto.CropPlaybookUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CropPlaybookService {

    private static final String RESOURCE = "CropPlaybook";

    private final CropPlaybookRepository repository;
    private final EventPublisher events;

    public CropPlaybookService(CropPlaybookRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<CropPlaybookResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(CropPlaybookResponse::from));
    }

    public CropPlaybookResponse get(UUID id) {
        return CropPlaybookResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public CropPlaybookResponse create(CropPlaybookCreateRequest request) {
        CropPlaybook entity = new CropPlaybook();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCropCode(request.cropCode());
        entity.setStageName(request.stageName());
        entity.setGuidance(request.guidance());
        entity.setInputRecommendations(request.inputRecommendations());
        entity.setRiskFactors(request.riskFactors());
        entity.setRevision(request.revision());

        CropPlaybook saved = repository.save(entity);
        events.publish("farm", "CropPlaybookCreated", saved.getId(), CropPlaybookResponse.from(saved));
        return CropPlaybookResponse.from(saved);
    }

    @Transactional
    public CropPlaybookResponse update(UUID id, CropPlaybookUpdateRequest request) {
        CropPlaybook entity = require(id);
        if (request.cropCode() != null) {
            entity.setCropCode(request.cropCode());
        }
        if (request.stageName() != null) {
            entity.setStageName(request.stageName());
        }
        if (request.guidance() != null) {
            entity.setGuidance(request.guidance());
        }
        if (request.inputRecommendations() != null) {
            entity.setInputRecommendations(request.inputRecommendations());
        }
        if (request.riskFactors() != null) {
            entity.setRiskFactors(request.riskFactors());
        }
        if (request.revision() != null) {
            entity.setRevision(request.revision());
        }

        CropPlaybook saved = repository.save(entity);
        events.publish("farm", "CropPlaybookUpdated", saved.getId(), CropPlaybookResponse.from(saved));
        return CropPlaybookResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        CropPlaybook entity = require(id);
        repository.delete(entity);
        events.publish("farm", "CropPlaybookDeleted", id, null);
    }

    private CropPlaybook require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
