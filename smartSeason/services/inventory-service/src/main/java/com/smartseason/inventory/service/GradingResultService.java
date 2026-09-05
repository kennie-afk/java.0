package com.smartseason.inventory.service;

import com.smartseason.inventory.domain.GradingResult;
import com.smartseason.inventory.platform.EventPublisher;
import com.smartseason.inventory.platform.PageResponse;
import com.smartseason.inventory.platform.ResourceNotFoundException;
import com.smartseason.inventory.platform.TenantContext;
import com.smartseason.inventory.repo.GradingResultRepository;
import com.smartseason.inventory.web.dto.GradingResultCreateRequest;
import com.smartseason.inventory.web.dto.GradingResultResponse;
import com.smartseason.inventory.web.dto.GradingResultUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GradingResultService {

    private static final String RESOURCE = "GradingResult";

    private final GradingResultRepository repository;
    private final EventPublisher events;

    public GradingResultService(GradingResultRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<GradingResultResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(GradingResultResponse::from));
    }

    public GradingResultResponse get(UUID id) {
        return GradingResultResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public GradingResultResponse create(GradingResultCreateRequest request) {
        GradingResult entity = new GradingResult();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setBatchId(request.batchId());
        entity.setGradedBy(request.gradedBy());
        entity.setGradedAt(request.gradedAt());
        entity.setAssignedGrade(request.assignedGrade());
        entity.setSizeMm(request.sizeMm());
        entity.setDefectPct(request.defectPct());
        entity.setMoisturePct(request.moisturePct());
        entity.setRejectedKg(request.rejectedKg());
        entity.setNotes(request.notes());
        entity.setStandardVersion(request.standardVersion());

        GradingResult saved = repository.save(entity);
        events.publish("market", "GradingResultCreated", saved.getId(), GradingResultResponse.from(saved));
        return GradingResultResponse.from(saved);
    }

    @Transactional
    public GradingResultResponse update(UUID id, GradingResultUpdateRequest request) {
        GradingResult entity = require(id);
        if (request.batchId() != null) {
            entity.setBatchId(request.batchId());
        }
        if (request.gradedBy() != null) {
            entity.setGradedBy(request.gradedBy());
        }
        if (request.gradedAt() != null) {
            entity.setGradedAt(request.gradedAt());
        }
        if (request.assignedGrade() != null) {
            entity.setAssignedGrade(request.assignedGrade());
        }
        if (request.sizeMm() != null) {
            entity.setSizeMm(request.sizeMm());
        }
        if (request.defectPct() != null) {
            entity.setDefectPct(request.defectPct());
        }
        if (request.moisturePct() != null) {
            entity.setMoisturePct(request.moisturePct());
        }
        if (request.rejectedKg() != null) {
            entity.setRejectedKg(request.rejectedKg());
        }
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }
        if (request.standardVersion() != null) {
            entity.setStandardVersion(request.standardVersion());
        }

        GradingResult saved = repository.save(entity);
        events.publish("market", "GradingResultUpdated", saved.getId(), GradingResultResponse.from(saved));
        return GradingResultResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        GradingResult entity = require(id);
        repository.delete(entity);
        events.publish("market", "GradingResultDeleted", id, null);
    }

    private GradingResult require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
