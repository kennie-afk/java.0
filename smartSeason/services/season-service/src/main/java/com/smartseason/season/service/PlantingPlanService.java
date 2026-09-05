package com.smartseason.season.service;

import com.smartseason.season.domain.PlantingPlan;
import com.smartseason.season.platform.EventPublisher;
import com.smartseason.season.platform.PageResponse;
import com.smartseason.season.platform.ResourceNotFoundException;
import com.smartseason.season.platform.TenantContext;
import com.smartseason.season.repo.PlantingPlanRepository;
import com.smartseason.season.web.dto.PlantingPlanCreateRequest;
import com.smartseason.season.web.dto.PlantingPlanResponse;
import com.smartseason.season.web.dto.PlantingPlanUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlantingPlanService {

    private static final String RESOURCE = "PlantingPlan";

    private final PlantingPlanRepository repository;
    private final EventPublisher events;

    public PlantingPlanService(PlantingPlanRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<PlantingPlanResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(PlantingPlanResponse::from));
    }

    public PlantingPlanResponse get(UUID id) {
        return PlantingPlanResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public PlantingPlanResponse create(PlantingPlanCreateRequest request) {
        PlantingPlan entity = new PlantingPlan();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setSeasonId(request.seasonId());
        entity.setSeedRateKgHa(request.seedRateKgHa());
        entity.setSpacingCm(request.spacingCm());
        entity.setTargetPopulation(request.targetPopulation());
        entity.setFertiliserPlan(request.fertiliserPlan());
        entity.setIrrigationPlan(request.irrigationPlan());
        entity.setApprovedBy(request.approvedBy());
        entity.setApprovedAt(request.approvedAt());

        PlantingPlan saved = repository.save(entity);
        events.publish("farm", "PlantingPlanCreated", saved.getId(), PlantingPlanResponse.from(saved));
        return PlantingPlanResponse.from(saved);
    }

    @Transactional
    public PlantingPlanResponse update(UUID id, PlantingPlanUpdateRequest request) {
        PlantingPlan entity = require(id);
        if (request.seasonId() != null) {
            entity.setSeasonId(request.seasonId());
        }
        if (request.seedRateKgHa() != null) {
            entity.setSeedRateKgHa(request.seedRateKgHa());
        }
        if (request.spacingCm() != null) {
            entity.setSpacingCm(request.spacingCm());
        }
        if (request.targetPopulation() != null) {
            entity.setTargetPopulation(request.targetPopulation());
        }
        if (request.fertiliserPlan() != null) {
            entity.setFertiliserPlan(request.fertiliserPlan());
        }
        if (request.irrigationPlan() != null) {
            entity.setIrrigationPlan(request.irrigationPlan());
        }
        if (request.approvedBy() != null) {
            entity.setApprovedBy(request.approvedBy());
        }
        if (request.approvedAt() != null) {
            entity.setApprovedAt(request.approvedAt());
        }

        PlantingPlan saved = repository.save(entity);
        events.publish("farm", "PlantingPlanUpdated", saved.getId(), PlantingPlanResponse.from(saved));
        return PlantingPlanResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        PlantingPlan entity = require(id);
        repository.delete(entity);
        events.publish("farm", "PlantingPlanDeleted", id, null);
    }

    private PlantingPlan require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
