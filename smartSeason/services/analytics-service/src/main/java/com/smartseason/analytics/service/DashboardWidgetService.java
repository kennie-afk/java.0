package com.smartseason.analytics.service;

import com.smartseason.analytics.domain.DashboardWidget;
import com.smartseason.analytics.platform.CountCache;
import com.smartseason.analytics.platform.CountCache;
import com.smartseason.analytics.platform.EventPublisher;
import com.smartseason.analytics.platform.Cursor;
import com.smartseason.analytics.platform.CursorPage;
import com.smartseason.analytics.platform.PageResponse;
import com.smartseason.analytics.platform.ResourceNotFoundException;
import com.smartseason.analytics.platform.TenantContext;
import com.smartseason.analytics.repo.DashboardWidgetRepository;
import com.smartseason.analytics.web.dto.DashboardWidgetCreateRequest;
import com.smartseason.analytics.web.dto.DashboardWidgetResponse;
import com.smartseason.analytics.web.dto.DashboardWidgetUpdateRequest;
import com.smartseason.analytics.platform.ListFilter;
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
public class DashboardWidgetService {

    private static final String RESOURCE = "DashboardWidget";
    private static final String ENTITY = "dashboard_widgets";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("dashboardCode", String.class),
            Map.entry("title", String.class),
            Map.entry("widgetType", DashboardWidget.WidgetType.class),
            Map.entry("metricKey", String.class));

    private static final List<String> SEARCHABLE = List.of("dashboardCode", "title", "metricKey");

    private final DashboardWidgetRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public DashboardWidgetService(DashboardWidgetRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<DashboardWidgetResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<DashboardWidget>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(DashboardWidgetResponse::from));
    }

    public PageResponse<DashboardWidgetResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(DashboardWidgetResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<DashboardWidgetResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<DashboardWidget> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(DashboardWidgetResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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
        counts.invalidate(ENTITY, saved.getTenantId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "DashboardWidgetDeleted", id, null);
    }

    private DashboardWidget require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
