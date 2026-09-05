package com.smartseason.telemetryingest.service;

import com.smartseason.telemetryingest.domain.TelemetryReading;
import com.smartseason.telemetryingest.platform.EventPublisher;
import com.smartseason.telemetryingest.platform.PageResponse;
import com.smartseason.telemetryingest.platform.ResourceNotFoundException;
import com.smartseason.telemetryingest.platform.TenantContext;
import com.smartseason.telemetryingest.repo.TelemetryReadingRepository;
import com.smartseason.telemetryingest.web.dto.TelemetryReadingCreateRequest;
import com.smartseason.telemetryingest.web.dto.TelemetryReadingResponse;
import com.smartseason.telemetryingest.web.dto.TelemetryReadingUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TelemetryReadingService {

    private static final String RESOURCE = "TelemetryReading";

    private final TelemetryReadingRepository repository;
    private final EventPublisher events;

    public TelemetryReadingService(TelemetryReadingRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<TelemetryReadingResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(TelemetryReadingResponse::from));
    }

    public TelemetryReadingResponse get(UUID id) {
        return TelemetryReadingResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public TelemetryReadingResponse create(TelemetryReadingCreateRequest request) {
        TelemetryReading entity = new TelemetryReading();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setDeviceId(request.deviceId());
        entity.setPlotId(request.plotId());
        entity.setMetric(request.metric());
        entity.setValue(request.value());
        entity.setUnit(request.unit());
        entity.setRecordedAt(request.recordedAt());
        entity.setReceivedAt(request.receivedAt());
        entity.setQuality(request.quality());
        entity.setRaw(request.raw());

        TelemetryReading saved = repository.save(entity);
        events.publish("iot", "TelemetryReadingCreated", saved.getId(), TelemetryReadingResponse.from(saved));
        return TelemetryReadingResponse.from(saved);
    }

    @Transactional
    public TelemetryReadingResponse update(UUID id, TelemetryReadingUpdateRequest request) {
        TelemetryReading entity = require(id);
        if (request.deviceId() != null) {
            entity.setDeviceId(request.deviceId());
        }
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.metric() != null) {
            entity.setMetric(request.metric());
        }
        if (request.value() != null) {
            entity.setValue(request.value());
        }
        if (request.unit() != null) {
            entity.setUnit(request.unit());
        }
        if (request.recordedAt() != null) {
            entity.setRecordedAt(request.recordedAt());
        }
        if (request.receivedAt() != null) {
            entity.setReceivedAt(request.receivedAt());
        }
        if (request.quality() != null) {
            entity.setQuality(request.quality());
        }
        if (request.raw() != null) {
            entity.setRaw(request.raw());
        }

        TelemetryReading saved = repository.save(entity);
        events.publish("iot", "TelemetryReadingUpdated", saved.getId(), TelemetryReadingResponse.from(saved));
        return TelemetryReadingResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        TelemetryReading entity = require(id);
        repository.delete(entity);
        events.publish("iot", "TelemetryReadingDeleted", id, null);
    }

    private TelemetryReading require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
