package com.smartseason.agronomy.service;

import com.smartseason.agronomy.domain.CropPlaybook;
import com.smartseason.agronomy.platform.CountCache;
import com.smartseason.agronomy.platform.CountCache;
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
    private static final String ENTITY = "crop_playbooks";

    private final CropPlaybookRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public CropPlaybookService(CropPlaybookRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<CropPlaybookResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(CropPlaybookResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
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
        counts.invalidate(ENTITY, saved.getTenantId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "CropPlaybookDeleted", id, null);
    }

    private CropPlaybook require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
