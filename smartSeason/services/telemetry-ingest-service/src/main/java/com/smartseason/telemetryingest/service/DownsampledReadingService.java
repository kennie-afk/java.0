package com.smartseason.telemetryingest.service;

import com.smartseason.telemetryingest.domain.DownsampledReading;
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

    private final DownsampledReadingRepository repository;
    private final EventPublisher events;

    public DownsampledReadingService(DownsampledReadingRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<DownsampledReadingResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(DownsampledReadingResponse::from));
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
        events.publish("iot", "DownsampledReadingDeleted", id, null);
    }

    private DownsampledReading require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
