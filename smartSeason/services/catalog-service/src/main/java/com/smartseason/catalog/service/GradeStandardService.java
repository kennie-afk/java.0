package com.smartseason.catalog.service;

import com.smartseason.catalog.domain.GradeStandard;
import com.smartseason.catalog.platform.EventPublisher;
import com.smartseason.catalog.platform.PageResponse;
import com.smartseason.catalog.platform.ResourceNotFoundException;
import com.smartseason.catalog.platform.TenantContext;
import com.smartseason.catalog.repo.GradeStandardRepository;
import com.smartseason.catalog.web.dto.GradeStandardCreateRequest;
import com.smartseason.catalog.web.dto.GradeStandardResponse;
import com.smartseason.catalog.web.dto.GradeStandardUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GradeStandardService {

    private static final String RESOURCE = "GradeStandard";

    private final GradeStandardRepository repository;
    private final EventPublisher events;

    public GradeStandardService(GradeStandardRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<GradeStandardResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(GradeStandardResponse::from));
    }

    public GradeStandardResponse get(UUID id) {
        return GradeStandardResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public GradeStandardResponse create(GradeStandardCreateRequest request) {
        GradeStandard entity = new GradeStandard();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCommodityCode(request.commodityCode());
        entity.setGrade(request.grade());
        entity.setCriteria(request.criteria());
        entity.setMinSizeMm(request.minSizeMm());
        entity.setMaxDefectPct(request.maxDefectPct());
        entity.setMoisturePctMax(request.moisturePctMax());
        entity.setRevision(request.revision());

        GradeStandard saved = repository.save(entity);
        events.publish("market", "GradeStandardCreated", saved.getId(), GradeStandardResponse.from(saved));
        return GradeStandardResponse.from(saved);
    }

    @Transactional
    public GradeStandardResponse update(UUID id, GradeStandardUpdateRequest request) {
        GradeStandard entity = require(id);
        if (request.commodityCode() != null) {
            entity.setCommodityCode(request.commodityCode());
        }
        if (request.grade() != null) {
            entity.setGrade(request.grade());
        }
        if (request.criteria() != null) {
            entity.setCriteria(request.criteria());
        }
        if (request.minSizeMm() != null) {
            entity.setMinSizeMm(request.minSizeMm());
        }
        if (request.maxDefectPct() != null) {
            entity.setMaxDefectPct(request.maxDefectPct());
        }
        if (request.moisturePctMax() != null) {
            entity.setMoisturePctMax(request.moisturePctMax());
        }
        if (request.revision() != null) {
            entity.setRevision(request.revision());
        }

        GradeStandard saved = repository.save(entity);
        events.publish("market", "GradeStandardUpdated", saved.getId(), GradeStandardResponse.from(saved));
        return GradeStandardResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        GradeStandard entity = require(id);
        repository.delete(entity);
        events.publish("market", "GradeStandardDeleted", id, null);
    }

    private GradeStandard require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
