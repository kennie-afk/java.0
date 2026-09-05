package com.smartseason.season.service;

import com.smartseason.season.domain.StageTemplate;
import com.smartseason.season.platform.EventPublisher;
import com.smartseason.season.platform.PageResponse;
import com.smartseason.season.platform.ResourceNotFoundException;
import com.smartseason.season.platform.TenantContext;
import com.smartseason.season.repo.StageTemplateRepository;
import com.smartseason.season.web.dto.StageTemplateCreateRequest;
import com.smartseason.season.web.dto.StageTemplateResponse;
import com.smartseason.season.web.dto.StageTemplateUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StageTemplateService {

    private static final String RESOURCE = "StageTemplate";

    private final StageTemplateRepository repository;
    private final EventPublisher events;

    public StageTemplateService(StageTemplateRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<StageTemplateResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(StageTemplateResponse::from));
    }

    public StageTemplateResponse get(UUID id) {
        return StageTemplateResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public StageTemplateResponse create(StageTemplateCreateRequest request) {
        StageTemplate entity = new StageTemplate();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCropCode(request.cropCode());
        entity.setStageName(request.stageName());
        entity.setSequence(request.sequence());
        entity.setDurationDays(request.durationDays());
        entity.setDescription(request.description());
        entity.setKeyActivities(request.keyActivities());

        StageTemplate saved = repository.save(entity);
        events.publish("farm", "StageTemplateCreated", saved.getId(), StageTemplateResponse.from(saved));
        return StageTemplateResponse.from(saved);
    }

    @Transactional
    public StageTemplateResponse update(UUID id, StageTemplateUpdateRequest request) {
        StageTemplate entity = require(id);
        if (request.cropCode() != null) {
            entity.setCropCode(request.cropCode());
        }
        if (request.stageName() != null) {
            entity.setStageName(request.stageName());
        }
        if (request.sequence() != null) {
            entity.setSequence(request.sequence());
        }
        if (request.durationDays() != null) {
            entity.setDurationDays(request.durationDays());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.keyActivities() != null) {
            entity.setKeyActivities(request.keyActivities());
        }

        StageTemplate saved = repository.save(entity);
        events.publish("farm", "StageTemplateUpdated", saved.getId(), StageTemplateResponse.from(saved));
        return StageTemplateResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        StageTemplate entity = require(id);
        repository.delete(entity);
        events.publish("farm", "StageTemplateDeleted", id, null);
    }

    private StageTemplate require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
