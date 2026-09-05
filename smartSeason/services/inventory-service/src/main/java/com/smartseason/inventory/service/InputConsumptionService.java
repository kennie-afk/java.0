package com.smartseason.inventory.service;

import com.smartseason.inventory.domain.InputConsumption;
import com.smartseason.inventory.platform.EventPublisher;
import com.smartseason.inventory.platform.PageResponse;
import com.smartseason.inventory.platform.ResourceNotFoundException;
import com.smartseason.inventory.platform.TenantContext;
import com.smartseason.inventory.repo.InputConsumptionRepository;
import com.smartseason.inventory.web.dto.InputConsumptionCreateRequest;
import com.smartseason.inventory.web.dto.InputConsumptionResponse;
import com.smartseason.inventory.web.dto.InputConsumptionUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InputConsumptionService {

    private static final String RESOURCE = "InputConsumption";

    private final InputConsumptionRepository repository;
    private final EventPublisher events;

    public InputConsumptionService(InputConsumptionRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<InputConsumptionResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(InputConsumptionResponse::from));
    }

    public InputConsumptionResponse get(UUID id) {
        return InputConsumptionResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public InputConsumptionResponse create(InputConsumptionCreateRequest request) {
        InputConsumption entity = new InputConsumption();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setInputIssueId(request.inputIssueId());
        entity.setFarmId(request.farmId());
        entity.setPlotId(request.plotId());
        entity.setSeasonId(request.seasonId());
        entity.setInputCode(request.inputCode());
        entity.setQuantity(request.quantity());
        entity.setUnit(request.unit());
        entity.setAppliedAt(request.appliedAt());
        entity.setAppliedBy(request.appliedBy());
        entity.setAreaCoveredHa(request.areaCoveredHa());
        entity.setEvidenceUrl(request.evidenceUrl());
        entity.setVarianceKg(request.varianceKg());

        InputConsumption saved = repository.save(entity);
        events.publish("market", "InputConsumptionCreated", saved.getId(), InputConsumptionResponse.from(saved));
        return InputConsumptionResponse.from(saved);
    }

    @Transactional
    public InputConsumptionResponse update(UUID id, InputConsumptionUpdateRequest request) {
        InputConsumption entity = require(id);
        if (request.inputIssueId() != null) {
            entity.setInputIssueId(request.inputIssueId());
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
        if (request.inputCode() != null) {
            entity.setInputCode(request.inputCode());
        }
        if (request.quantity() != null) {
            entity.setQuantity(request.quantity());
        }
        if (request.unit() != null) {
            entity.setUnit(request.unit());
        }
        if (request.appliedAt() != null) {
            entity.setAppliedAt(request.appliedAt());
        }
        if (request.appliedBy() != null) {
            entity.setAppliedBy(request.appliedBy());
        }
        if (request.areaCoveredHa() != null) {
            entity.setAreaCoveredHa(request.areaCoveredHa());
        }
        if (request.evidenceUrl() != null) {
            entity.setEvidenceUrl(request.evidenceUrl());
        }
        if (request.varianceKg() != null) {
            entity.setVarianceKg(request.varianceKg());
        }

        InputConsumption saved = repository.save(entity);
        events.publish("market", "InputConsumptionUpdated", saved.getId(), InputConsumptionResponse.from(saved));
        return InputConsumptionResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        InputConsumption entity = require(id);
        repository.delete(entity);
        events.publish("market", "InputConsumptionDeleted", id, null);
    }

    private InputConsumption require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
