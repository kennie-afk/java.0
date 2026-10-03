package com.smartseason.telemetryingest.service;

import com.smartseason.telemetryingest.domain.DownsampledReading;
import com.smartseason.telemetryingest.platform.CountCache;
import com.smartseason.telemetryingest.platform.CountCache;
import com.smartseason.telemetryingest.platform.EventPublisher;
import com.smartseason.telemetryingest.platform.Cursor;
import com.smartseason.telemetryingest.platform.CursorPage;
import com.smartseason.telemetryingest.platform.PageResponse;
import com.smartseason.telemetryingest.platform.ResourceNotFoundException;
import com.smartseason.telemetryingest.platform.TenantContext;
import com.smartseason.telemetryingest.repo.DownsampledReadingRepository;
import com.smartseason.telemetryingest.web.dto.DownsampledReadingCreateRequest;
import com.smartseason.telemetryingest.web.dto.DownsampledReadingResponse;
import com.smartseason.telemetryingest.web.dto.DownsampledReadingUpdateRequest;
import com.smartseason.telemetryingest.platform.ListFilter;
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
public class DownsampledReadingService {

    private static final String RESOURCE = "DownsampledReading";
    private static final String ENTITY = "downsampled_readings";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("deviceId", UUID.class),
            Map.entry("metric", String.class));

    private static final List<String> SEARCHABLE = List.of("metric");

    private final DownsampledReadingRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public DownsampledReadingService(DownsampledReadingRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<DownsampledReadingResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<DownsampledReading>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(DownsampledReadingResponse::from));
    }

    public PageResponse<DownsampledReadingResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(DownsampledReadingResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<DownsampledReadingResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<DownsampledReading> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(DownsampledReadingResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public DownsampledReadingResponse get(UUID id) {
        return DownsampledReadingResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public DownsampledReadingResponse create(DownsampledReadingCreateRequest request) {
        DownsampledReading entity = new DownsampledReading();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setDeviceId(request.deviceId());
        entity.setMetric(request.metric());
        entity.setBucketStart(request.bucketStart());
        entity.setBucketMinutes(request.bucketMinutes());
        entity.setAvgValue(request.avgValue());
        entity.setMinValue(request.minValue());
        entity.setMaxValue(request.maxValue());
        entity.setSampleCount(request.sampleCount());

        DownsampledReading saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("iot", "DownsampledReadingCreated", saved.getId(), DownsampledReadingResponse.from(saved));
        return DownsampledReadingResponse.from(saved);
    }

    @Transactional
    public DownsampledReadingResponse update(UUID id, DownsampledReadingUpdateRequest request) {
        DownsampledReading entity = require(id);
        if (request.deviceId() != null) {
            entity.setDeviceId(request.deviceId());
        }
        if (request.metric() != null) {
            entity.setMetric(request.metric());
        }
        if (request.bucketStart() != null) {
            entity.setBucketStart(request.bucketStart());
        }
        if (request.bucketMinutes() != null) {
            entity.setBucketMinutes(request.bucketMinutes());
        }
        if (request.avgValue() != null) {
            entity.setAvgValue(request.avgValue());
        }
        if (request.minValue() != null) {
            entity.setMinValue(request.minValue());
        }
        if (request.maxValue() != null) {
            entity.setMaxValue(request.maxValue());
        }
        if (request.sampleCount() != null) {
            entity.setSampleCount(request.sampleCount());
        }

        DownsampledReading saved = repository.save(entity);
        events.publish("iot", "DownsampledReadingUpdated", saved.getId(), DownsampledReadingResponse.from(saved));
        return DownsampledReadingResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        DownsampledReading entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("iot", "DownsampledReadingDeleted", id, null);
    }

    private DownsampledReading require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
