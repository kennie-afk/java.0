package com.smartseason.analytics.service;

import com.smartseason.analytics.domain.ReportRun;
import com.smartseason.analytics.platform.EventPublisher;
import com.smartseason.analytics.platform.PageResponse;
import com.smartseason.analytics.platform.ResourceNotFoundException;
import com.smartseason.analytics.platform.TenantContext;
import com.smartseason.analytics.repo.ReportRunRepository;
import com.smartseason.analytics.web.dto.ReportRunCreateRequest;
import com.smartseason.analytics.web.dto.ReportRunResponse;
import com.smartseason.analytics.web.dto.ReportRunUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportRunService {

    private static final String RESOURCE = "ReportRun";

    private final ReportRunRepository repository;
    private final EventPublisher events;

    public ReportRunService(ReportRunRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<ReportRunResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(ReportRunResponse::from));
    }

    public ReportRunResponse get(UUID id) {
        return ReportRunResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ReportRunResponse create(ReportRunCreateRequest request) {
        ReportRun entity = new ReportRun();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setReportId(request.reportId());
        entity.setReportCode(request.reportCode());
        entity.setTriggeredBy(request.triggeredBy());
        entity.setStartedAt(request.startedAt());
        entity.setCompletedAt(request.completedAt());
        entity.setRowCount(request.rowCount());
        entity.setOutputUrl(request.outputUrl());
        entity.setParameters(request.parameters());
        entity.setStatus(request.status());
        entity.setError(request.error());

        ReportRun saved = repository.save(entity);
        events.publish("platform", "ReportRunCreated", saved.getId(), ReportRunResponse.from(saved));
        return ReportRunResponse.from(saved);
    }

    @Transactional
    public ReportRunResponse update(UUID id, ReportRunUpdateRequest request) {
        ReportRun entity = require(id);
        if (request.reportId() != null) {
            entity.setReportId(request.reportId());
        }
        if (request.reportCode() != null) {
            entity.setReportCode(request.reportCode());
        }
        if (request.triggeredBy() != null) {
            entity.setTriggeredBy(request.triggeredBy());
        }
        if (request.startedAt() != null) {
            entity.setStartedAt(request.startedAt());
        }
        if (request.completedAt() != null) {
            entity.setCompletedAt(request.completedAt());
        }
        if (request.rowCount() != null) {
            entity.setRowCount(request.rowCount());
        }
        if (request.outputUrl() != null) {
            entity.setOutputUrl(request.outputUrl());
        }
        if (request.parameters() != null) {
            entity.setParameters(request.parameters());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.error() != null) {
            entity.setError(request.error());
        }

        ReportRun saved = repository.save(entity);
        events.publish("platform", "ReportRunUpdated", saved.getId(), ReportRunResponse.from(saved));
        return ReportRunResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        ReportRun entity = require(id);
        repository.delete(entity);
        events.publish("platform", "ReportRunDeleted", id, null);
    }

    private ReportRun require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
