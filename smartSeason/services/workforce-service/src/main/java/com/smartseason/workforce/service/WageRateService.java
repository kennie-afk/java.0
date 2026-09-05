package com.smartseason.workforce.service;

import com.smartseason.workforce.domain.WageRate;
import com.smartseason.workforce.platform.EventPublisher;
import com.smartseason.workforce.platform.PageResponse;
import com.smartseason.workforce.platform.ResourceNotFoundException;
import com.smartseason.workforce.platform.TenantContext;
import com.smartseason.workforce.repo.WageRateRepository;
import com.smartseason.workforce.web.dto.WageRateCreateRequest;
import com.smartseason.workforce.web.dto.WageRateResponse;
import com.smartseason.workforce.web.dto.WageRateUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WageRateService {

    private static final String RESOURCE = "WageRate";

    private final WageRateRepository repository;
    private final EventPublisher events;

    public WageRateService(WageRateRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<WageRateResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(WageRateResponse::from));
    }

    public WageRateResponse get(UUID id) {
        return WageRateResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public WageRateResponse create(WageRateCreateRequest request) {
        WageRate entity = new WageRate();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setFarmId(request.farmId());
        entity.setTaskCode(request.taskCode());
        entity.setRateType(request.rateType());
        entity.setAmount(request.amount());
        entity.setCurrency(request.currency());
        entity.setUnit(request.unit());
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setEffectiveTo(request.effectiveTo());

        WageRate saved = repository.save(entity);
        events.publish("workforce", "WageRateCreated", saved.getId(), WageRateResponse.from(saved));
        return WageRateResponse.from(saved);
    }

    @Transactional
    public WageRateResponse update(UUID id, WageRateUpdateRequest request) {
        WageRate entity = require(id);
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.taskCode() != null) {
            entity.setTaskCode(request.taskCode());
        }
        if (request.rateType() != null) {
            entity.setRateType(request.rateType());
        }
        if (request.amount() != null) {
            entity.setAmount(request.amount());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.unit() != null) {
            entity.setUnit(request.unit());
        }
        if (request.effectiveFrom() != null) {
            entity.setEffectiveFrom(request.effectiveFrom());
        }
        if (request.effectiveTo() != null) {
            entity.setEffectiveTo(request.effectiveTo());
        }

        WageRate saved = repository.save(entity);
        events.publish("workforce", "WageRateUpdated", saved.getId(), WageRateResponse.from(saved));
        return WageRateResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        WageRate entity = require(id);
        repository.delete(entity);
        events.publish("workforce", "WageRateDeleted", id, null);
    }

    private WageRate require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
