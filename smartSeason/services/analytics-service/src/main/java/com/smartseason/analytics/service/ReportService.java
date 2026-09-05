package com.smartseason.analytics.service;

import com.smartseason.analytics.domain.Report;
import com.smartseason.analytics.platform.EventPublisher;
import com.smartseason.analytics.platform.PageResponse;
import com.smartseason.analytics.platform.ResourceNotFoundException;
import com.smartseason.analytics.platform.TenantContext;
import com.smartseason.analytics.repo.ReportRepository;
import com.smartseason.analytics.web.dto.ReportCreateRequest;
import com.smartseason.analytics.web.dto.ReportResponse;
import com.smartseason.analytics.web.dto.ReportUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final String RESOURCE = "Report";

    private final ReportRepository repository;
    private final EventPublisher events;

    public ReportService(ReportRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<ReportResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(ReportResponse::from));
    }

    public ReportResponse get(UUID id) {
        return ReportResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ReportResponse create(ReportCreateRequest request) {
        Report entity = new Report();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setCategory(request.category());
        entity.setQuerySpec(request.querySpec());
        entity.setSchedule(request.schedule());
        entity.setFormat(request.format());
        entity.setEnabled(request.enabled());
        entity.setOwnerUserId(request.ownerUserId());

        Report saved = repository.save(entity);
        events.publish("platform", "ReportCreated", saved.getId(), ReportResponse.from(saved));
        return ReportResponse.from(saved);
    }

    @Transactional
    public ReportResponse update(UUID id, ReportUpdateRequest request) {
        Report entity = require(id);
        if (request.code() != null) {
            entity.setCode(request.code());
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.category() != null) {
            entity.setCategory(request.category());
        }
        if (request.querySpec() != null) {
            entity.setQuerySpec(request.querySpec());
        }
        if (request.schedule() != null) {
            entity.setSchedule(request.schedule());
        }
        if (request.format() != null) {
            entity.setFormat(request.format());
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        if (request.ownerUserId() != null) {
            entity.setOwnerUserId(request.ownerUserId());
        }

        Report saved = repository.save(entity);
        events.publish("platform", "ReportUpdated", saved.getId(), ReportResponse.from(saved));
        return ReportResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Report entity = require(id);
        repository.delete(entity);
        events.publish("platform", "ReportDeleted", id, null);
    }

    private Report require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
