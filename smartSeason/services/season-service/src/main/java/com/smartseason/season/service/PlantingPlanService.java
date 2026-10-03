package com.smartseason.season.service;

import com.smartseason.season.domain.PlantingPlan;
import com.smartseason.season.platform.CountCache;
import com.smartseason.season.platform.CountCache;
import com.smartseason.season.platform.EventPublisher;
import com.smartseason.season.platform.Cursor;
import com.smartseason.season.platform.CursorPage;
import com.smartseason.season.platform.PageResponse;
import com.smartseason.season.platform.ResourceNotFoundException;
import com.smartseason.season.platform.TenantContext;
import com.smartseason.season.repo.PlantingPlanRepository;
import com.smartseason.season.web.dto.PlantingPlanCreateRequest;
import com.smartseason.season.web.dto.PlantingPlanResponse;
import com.smartseason.season.web.dto.PlantingPlanUpdateRequest;
import com.smartseason.season.platform.ListFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlantingPlanService {

    private static final String RESOURCE = "PlantingPlan";
    private static final String ENTITY = "planting_plans";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("seasonId", UUID.class),
            Map.entry("spacingCm", String.class),
            Map.entry("approvedBy", UUID.class));

    private static final List<String> SEARCHABLE = List.of("spacingCm");

    private final PlantingPlanRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public PlantingPlanService(PlantingPlanRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<PlantingPlanResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<PlantingPlan>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(PlantingPlanResponse::from));
    }

    public PageResponse<PlantingPlanResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(PlantingPlanResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<PlantingPlanResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<PlantingPlan> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(PlantingPlanResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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
        counts.invalidate(ENTITY, saved.getTenantId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "PlantingPlanDeleted", id, null);
    }

    private PlantingPlan require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
