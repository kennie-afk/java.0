package com.smartseason.analytics.service;

import com.smartseason.analytics.domain.DashboardWidget;
import com.smartseason.analytics.platform.EventPublisher;
import com.smartseason.analytics.platform.PageResponse;
import com.smartseason.analytics.platform.ResourceNotFoundException;
import com.smartseason.analytics.platform.TenantContext;
import com.smartseason.analytics.repo.DashboardWidgetRepository;
import com.smartseason.analytics.web.dto.DashboardWidgetCreateRequest;
import com.smartseason.analytics.web.dto.DashboardWidgetResponse;
import com.smartseason.analytics.web.dto.DashboardWidgetUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardWidgetService {

    private static final String RESOURCE = "DashboardWidget";

    private final DashboardWidgetRepository repository;
    private final EventPublisher events;

    public DashboardWidgetService(DashboardWidgetRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<DashboardWidgetResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(DashboardWidgetResponse::from));
    }

    public DashboardWidgetResponse get(UUID id) {
        return DashboardWidgetResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public DashboardWidgetResponse create(DashboardWidgetCreateRequest request) {
        DashboardWidget entity = new DashboardWidget();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setDashboardCode(request.dashboardCode());
        entity.setTitle(request.title());
        entity.setWidgetType(request.widgetType());
        entity.setMetricKey(request.metricKey());
        entity.setQuerySpec(request.querySpec());
        entity.setPosition(request.position());
        entity.setWidth(request.width());
        entity.setConfig(request.config());

        DashboardWidget saved = repository.save(entity);
        events.publish("platform", "DashboardWidgetCreated", saved.getId(), DashboardWidgetResponse.from(saved));
        return DashboardWidgetResponse.from(saved);
    }

    @Transactional
    public DashboardWidgetResponse update(UUID id, DashboardWidgetUpdateRequest request) {
        DashboardWidget entity = require(id);
        if (request.dashboardCode() != null) {
            entity.setDashboardCode(request.dashboardCode());
        }
        if (request.title() != null) {
            entity.setTitle(request.title());
        }
        if (request.widgetType() != null) {
            entity.setWidgetType(request.widgetType());
        }
        if (request.metricKey() != null) {
            entity.setMetricKey(request.metricKey());
        }
        if (request.querySpec() != null) {
            entity.setQuerySpec(request.querySpec());
        }
        if (request.position() != null) {
            entity.setPosition(request.position());
        }
        if (request.width() != null) {
            entity.setWidth(request.width());
        }
        if (request.config() != null) {
            entity.setConfig(request.config());
        }

        DashboardWidget saved = repository.save(entity);
        events.publish("platform", "DashboardWidgetUpdated", saved.getId(), DashboardWidgetResponse.from(saved));
        return DashboardWidgetResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        DashboardWidget entity = require(id);
        repository.delete(entity);
        events.publish("platform", "DashboardWidgetDeleted", id, null);
    }

    private DashboardWidget require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
