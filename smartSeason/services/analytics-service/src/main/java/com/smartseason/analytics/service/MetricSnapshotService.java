package com.smartseason.analytics.service;

import com.smartseason.analytics.domain.MetricSnapshot;
import com.smartseason.analytics.platform.CountCache;
import com.smartseason.analytics.platform.CountCache;
import com.smartseason.analytics.platform.EventPublisher;
import com.smartseason.analytics.platform.ReferenceChecker;
import com.smartseason.analytics.platform.Cursor;
import com.smartseason.analytics.platform.CursorPage;
import com.smartseason.analytics.platform.PageResponse;
import com.smartseason.analytics.platform.ResourceNotFoundException;
import com.smartseason.analytics.platform.TenantContext;
import com.smartseason.analytics.repo.MetricSnapshotRepository;
import com.smartseason.analytics.web.dto.MetricSnapshotCreateRequest;
import com.smartseason.analytics.web.dto.MetricSnapshotResponse;
import com.smartseason.analytics.web.dto.MetricSnapshotUpdateRequest;
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
public class MetricSnapshotService {

    private static final String RESOURCE = "MetricSnapshot";
    private static final String ENTITY = "metric_snapshots";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("metricKey", String.class),
            Map.entry("dimension", String.class),
            Map.entry("dimensionValue", String.class),
            Map.entry("granularity", MetricSnapshot.Granularity.class),
            Map.entry("unit", String.class));

    private static final List<String> SEARCHABLE = List.of("metricKey", "dimension", "dimensionValue", "unit");

    private final MetricSnapshotRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public MetricSnapshotService(MetricSnapshotRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<MetricSnapshotResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<MetricSnapshot>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(MetricSnapshotResponse::from));
    }

    public PageResponse<MetricSnapshotResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(MetricSnapshotResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<MetricSnapshotResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<MetricSnapshot> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(MetricSnapshotResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public MetricSnapshotResponse get(UUID id) {
        return MetricSnapshotResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public MetricSnapshotResponse create(MetricSnapshotCreateRequest request) {
        MetricSnapshot entity = new MetricSnapshot();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setMetricKey(request.metricKey());
        entity.setDimension(request.dimension());
        entity.setDimensionValue(request.dimensionValue());
        entity.setPeriodStart(request.periodStart());
        entity.setPeriodEnd(request.periodEnd());
        entity.setGranularity(request.granularity());
        entity.setValue(request.value());
        entity.setUnit(request.unit());
        entity.setComputedAt(request.computedAt());

        MetricSnapshot saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("platform", "MetricSnapshotCreated", saved.getId(), MetricSnapshotResponse.from(saved));
        return MetricSnapshotResponse.from(saved);
    }

    @Transactional
    public MetricSnapshotResponse update(UUID id, MetricSnapshotUpdateRequest request) {
        MetricSnapshot entity = require(id);
        if (request.metricKey() != null) {
            entity.setMetricKey(request.metricKey());
        }
        if (request.dimension() != null) {
            entity.setDimension(request.dimension());
        }
        if (request.dimensionValue() != null) {
            entity.setDimensionValue(request.dimensionValue());
        }
        if (request.periodStart() != null) {
            entity.setPeriodStart(request.periodStart());
        }
        if (request.periodEnd() != null) {
            entity.setPeriodEnd(request.periodEnd());
        }
        if (request.granularity() != null) {
            entity.setGranularity(request.granularity());
        }
        if (request.value() != null) {
            entity.setValue(request.value());
        }
        if (request.unit() != null) {
            entity.setUnit(request.unit());
        }
        if (request.computedAt() != null) {
            entity.setComputedAt(request.computedAt());
        }

        MetricSnapshot saved = repository.save(entity);
        events.publish("platform", "MetricSnapshotUpdated", saved.getId(), MetricSnapshotResponse.from(saved));
        return MetricSnapshotResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        MetricSnapshot entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "MetricSnapshotDeleted", id, null);
    }

    private MetricSnapshot require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
