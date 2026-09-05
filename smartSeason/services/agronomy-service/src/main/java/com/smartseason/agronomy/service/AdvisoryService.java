package com.smartseason.agronomy.service;

import com.smartseason.agronomy.domain.Advisory;
import com.smartseason.agronomy.platform.EventPublisher;
import com.smartseason.agronomy.platform.PageResponse;
import com.smartseason.agronomy.platform.ResourceNotFoundException;
import com.smartseason.agronomy.platform.TenantContext;
import com.smartseason.agronomy.repo.AdvisoryRepository;
import com.smartseason.agronomy.web.dto.AdvisoryCreateRequest;
import com.smartseason.agronomy.web.dto.AdvisoryResponse;
import com.smartseason.agronomy.web.dto.AdvisoryUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdvisoryService {

    private static final String RESOURCE = "Advisory";

    private final AdvisoryRepository repository;
    private final EventPublisher events;

    public AdvisoryService(AdvisoryRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<AdvisoryResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(AdvisoryResponse::from));
    }

    public AdvisoryResponse get(UUID id) {
        return AdvisoryResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public AdvisoryResponse create(AdvisoryCreateRequest request) {
        Advisory entity = new Advisory();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setSeasonId(request.seasonId());
        entity.setPlotId(request.plotId());
        entity.setCropCode(request.cropCode());
        entity.setTitle(request.title());
        entity.setBody(request.body());
        entity.setSeverity(request.severity());
        entity.setSource(request.source());
        entity.setIssuedAt(request.issuedAt());
        entity.setAcknowledgedAt(request.acknowledgedAt());
        entity.setAcknowledgedBy(request.acknowledgedBy());

        Advisory saved = repository.save(entity);
        events.publish("farm", "AdvisoryCreated", saved.getId(), AdvisoryResponse.from(saved));
        return AdvisoryResponse.from(saved);
    }

    @Transactional
    public AdvisoryResponse update(UUID id, AdvisoryUpdateRequest request) {
        Advisory entity = require(id);
        if (request.seasonId() != null) {
            entity.setSeasonId(request.seasonId());
        }
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.cropCode() != null) {
            entity.setCropCode(request.cropCode());
        }
        if (request.title() != null) {
            entity.setTitle(request.title());
        }
        if (request.body() != null) {
            entity.setBody(request.body());
        }
        if (request.severity() != null) {
            entity.setSeverity(request.severity());
        }
        if (request.source() != null) {
            entity.setSource(request.source());
        }
        if (request.issuedAt() != null) {
            entity.setIssuedAt(request.issuedAt());
        }
        if (request.acknowledgedAt() != null) {
            entity.setAcknowledgedAt(request.acknowledgedAt());
        }
        if (request.acknowledgedBy() != null) {
            entity.setAcknowledgedBy(request.acknowledgedBy());
        }

        Advisory saved = repository.save(entity);
        events.publish("farm", "AdvisoryUpdated", saved.getId(), AdvisoryResponse.from(saved));
        return AdvisoryResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Advisory entity = require(id);
        repository.delete(entity);
        events.publish("farm", "AdvisoryDeleted", id, null);
    }

    private Advisory require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
