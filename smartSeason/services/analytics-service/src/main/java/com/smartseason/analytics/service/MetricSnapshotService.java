package com.smartseason.analytics.service;

import com.smartseason.analytics.domain.MetricSnapshot;
import com.smartseason.analytics.platform.CountCache;
import com.smartseason.analytics.platform.CountCache;
import com.smartseason.analytics.platform.EventPublisher;
import com.smartseason.analytics.platform.PageResponse;
import com.smartseason.analytics.platform.ResourceNotFoundException;
import com.smartseason.analytics.platform.TenantContext;
import com.smartseason.analytics.repo.MetricSnapshotRepository;
import com.smartseason.analytics.web.dto.MetricSnapshotCreateRequest;
import com.smartseason.analytics.web.dto.MetricSnapshotResponse;
import com.smartseason.analytics.web.dto.MetricSnapshotUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MetricSnapshotService {

    private static final String RESOURCE = "MetricSnapshot";
    private static final String ENTITY = "metric_snapshots";

    private final MetricSnapshotRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public MetricSnapshotService(MetricSnapshotRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<MetricSnapshotResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(MetricSnapshotResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
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
