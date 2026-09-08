package com.smartseason.farm.service;

import com.smartseason.farm.domain.Plot;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.EventPublisher;
import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.platform.ResourceNotFoundException;
import com.smartseason.farm.platform.TenantContext;
import com.smartseason.farm.repo.PlotRepository;
import com.smartseason.farm.web.dto.PlotCreateRequest;
import com.smartseason.farm.web.dto.PlotResponse;
import com.smartseason.farm.web.dto.PlotUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlotService {

    private static final String RESOURCE = "Plot";
    private static final String ENTITY = "plots";

    private final PlotRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public PlotService(PlotRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<PlotResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(PlotResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public PlotResponse get(UUID id) {
        return PlotResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public PlotResponse create(PlotCreateRequest request) {
        Plot entity = new Plot();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setFarmId(request.farmId());
        entity.setName(request.name());
        entity.setAreaHa(request.areaHa());
        entity.setBoundaryGeojson(request.boundaryGeojson());
        entity.setCentroidLat(request.centroidLat());
        entity.setCentroidLng(request.centroidLng());
        entity.setIrrigated(request.irrigated());
        entity.setCurrentCrop(request.currentCrop());
        entity.setStatus(request.status());

        Plot saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("farm", "PlotCreated", saved.getId(), PlotResponse.from(saved));
        return PlotResponse.from(saved);
    }

    @Transactional
    public PlotResponse update(UUID id, PlotUpdateRequest request) {
        Plot entity = require(id);
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.areaHa() != null) {
            entity.setAreaHa(request.areaHa());
        }
        if (request.boundaryGeojson() != null) {
            entity.setBoundaryGeojson(request.boundaryGeojson());
        }
        if (request.centroidLat() != null) {
            entity.setCentroidLat(request.centroidLat());
        }
        if (request.centroidLng() != null) {
            entity.setCentroidLng(request.centroidLng());
        }
        if (request.irrigated() != null) {
            entity.setIrrigated(request.irrigated());
        }
        if (request.currentCrop() != null) {
            entity.setCurrentCrop(request.currentCrop());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        Plot saved = repository.save(entity);
        events.publish("farm", "PlotUpdated", saved.getId(), PlotResponse.from(saved));
        return PlotResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Plot entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "PlotDeleted", id, null);
    }

    private Plot require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
