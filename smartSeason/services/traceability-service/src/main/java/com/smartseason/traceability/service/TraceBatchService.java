package com.smartseason.traceability.service;

import com.smartseason.traceability.domain.TraceBatch;
import com.smartseason.traceability.platform.EventPublisher;
import com.smartseason.traceability.platform.PageResponse;
import com.smartseason.traceability.platform.ResourceNotFoundException;
import com.smartseason.traceability.platform.TenantContext;
import com.smartseason.traceability.repo.TraceBatchRepository;
import com.smartseason.traceability.web.dto.TraceBatchCreateRequest;
import com.smartseason.traceability.web.dto.TraceBatchResponse;
import com.smartseason.traceability.web.dto.TraceBatchUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TraceBatchService {

    private static final String RESOURCE = "TraceBatch";

    private final TraceBatchRepository repository;
    private final EventPublisher events;

    public TraceBatchService(TraceBatchRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<TraceBatchResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(TraceBatchResponse::from));
    }

    public TraceBatchResponse get(UUID id) {
        return TraceBatchResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public TraceBatchResponse create(TraceBatchCreateRequest request) {
        TraceBatch entity = new TraceBatch();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setBatchCode(request.batchCode());
        entity.setCommodityCode(request.commodityCode());
        entity.setFarmId(request.farmId());
        entity.setPlotId(request.plotId());
        entity.setSeasonId(request.seasonId());
        entity.setHarvestedOn(request.harvestedOn());
        entity.setOriginCounty(request.originCounty());
        entity.setCurrentHolderOrgId(request.currentHolderOrgId());
        entity.setQuantityKg(request.quantityKg());
        entity.setStatus(request.status());

        TraceBatch saved = repository.save(entity);
        events.publish("platform", "TraceBatchCreated", saved.getId(), TraceBatchResponse.from(saved));
        return TraceBatchResponse.from(saved);
    }

    @Transactional
    public TraceBatchResponse update(UUID id, TraceBatchUpdateRequest request) {
        TraceBatch entity = require(id);
        if (request.batchCode() != null) {
            entity.setBatchCode(request.batchCode());
        }
        if (request.commodityCode() != null) {
            entity.setCommodityCode(request.commodityCode());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.seasonId() != null) {
            entity.setSeasonId(request.seasonId());
        }
        if (request.harvestedOn() != null) {
            entity.setHarvestedOn(request.harvestedOn());
        }
        if (request.originCounty() != null) {
            entity.setOriginCounty(request.originCounty());
        }
        if (request.currentHolderOrgId() != null) {
            entity.setCurrentHolderOrgId(request.currentHolderOrgId());
        }
        if (request.quantityKg() != null) {
            entity.setQuantityKg(request.quantityKg());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        TraceBatch saved = repository.save(entity);
        events.publish("platform", "TraceBatchUpdated", saved.getId(), TraceBatchResponse.from(saved));
        return TraceBatchResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        TraceBatch entity = require(id);
        repository.delete(entity);
        events.publish("platform", "TraceBatchDeleted", id, null);
    }

    private TraceBatch require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
