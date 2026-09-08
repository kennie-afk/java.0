package com.smartseason.telemetryingest.service;

import com.smartseason.telemetryingest.domain.DownsampledReading;
import com.smartseason.telemetryingest.platform.CountCache;
import com.smartseason.telemetryingest.platform.CountCache;
import com.smartseason.telemetryingest.platform.EventPublisher;
import com.smartseason.telemetryingest.platform.PageResponse;
import com.smartseason.telemetryingest.platform.ResourceNotFoundException;
import com.smartseason.telemetryingest.platform.TenantContext;
import com.smartseason.telemetryingest.repo.DownsampledReadingRepository;
import com.smartseason.telemetryingest.web.dto.DownsampledReadingCreateRequest;
import com.smartseason.telemetryingest.web.dto.DownsampledReadingResponse;
import com.smartseason.telemetryingest.web.dto.DownsampledReadingUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DownsampledReadingService {

    private static final String RESOURCE = "DownsampledReading";
    private static final String ENTITY = "downsampled_readings";

    private final DownsampledReadingRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public DownsampledReadingService(DownsampledReadingRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<DownsampledReadingResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(DownsampledReadingResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
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
