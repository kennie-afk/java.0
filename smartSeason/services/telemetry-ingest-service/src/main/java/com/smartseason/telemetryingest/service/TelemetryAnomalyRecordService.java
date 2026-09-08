package com.smartseason.telemetryingest.service;

import com.smartseason.telemetryingest.domain.TelemetryAnomalyRecord;
import com.smartseason.telemetryingest.platform.CountCache;
import com.smartseason.telemetryingest.platform.CountCache;
import com.smartseason.telemetryingest.platform.EventPublisher;
import com.smartseason.telemetryingest.platform.PageResponse;
import com.smartseason.telemetryingest.platform.ResourceNotFoundException;
import com.smartseason.telemetryingest.platform.TenantContext;
import com.smartseason.telemetryingest.repo.TelemetryAnomalyRecordRepository;
import com.smartseason.telemetryingest.web.dto.TelemetryAnomalyRecordCreateRequest;
import com.smartseason.telemetryingest.web.dto.TelemetryAnomalyRecordResponse;
import com.smartseason.telemetryingest.web.dto.TelemetryAnomalyRecordUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TelemetryAnomalyRecordService {

    private static final String RESOURCE = "TelemetryAnomalyRecord";
    private static final String ENTITY = "telemetry_anomalies";

    private final TelemetryAnomalyRecordRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public TelemetryAnomalyRecordService(TelemetryAnomalyRecordRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<TelemetryAnomalyRecordResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(TelemetryAnomalyRecordResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public TelemetryAnomalyRecordResponse get(UUID id) {
        return TelemetryAnomalyRecordResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public TelemetryAnomalyRecordResponse create(TelemetryAnomalyRecordCreateRequest request) {
        TelemetryAnomalyRecord entity = new TelemetryAnomalyRecord();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setDeviceId(request.deviceId());
        entity.setPlotId(request.plotId());
        entity.setMetric(request.metric());
        entity.setObservedValue(request.observedValue());
        entity.setExpectedMin(request.expectedMin());
        entity.setExpectedMax(request.expectedMax());
        entity.setDetectedAt(request.detectedAt());
        entity.setSeverity(request.severity());
        entity.setResolved(request.resolved());

        TelemetryAnomalyRecord saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("iot", "TelemetryAnomalyRecordCreated", saved.getId(), TelemetryAnomalyRecordResponse.from(saved));
        return TelemetryAnomalyRecordResponse.from(saved);
    }

    @Transactional
    public TelemetryAnomalyRecordResponse update(UUID id, TelemetryAnomalyRecordUpdateRequest request) {
        TelemetryAnomalyRecord entity = require(id);
        if (request.deviceId() != null) {
            entity.setDeviceId(request.deviceId());
        }
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.metric() != null) {
            entity.setMetric(request.metric());
        }
        if (request.observedValue() != null) {
            entity.setObservedValue(request.observedValue());
        }
        if (request.expectedMin() != null) {
            entity.setExpectedMin(request.expectedMin());
        }
        if (request.expectedMax() != null) {
            entity.setExpectedMax(request.expectedMax());
        }
        if (request.detectedAt() != null) {
            entity.setDetectedAt(request.detectedAt());
        }
        if (request.severity() != null) {
            entity.setSeverity(request.severity());
        }
        if (request.resolved() != null) {
            entity.setResolved(request.resolved());
        }

        TelemetryAnomalyRecord saved = repository.save(entity);
        events.publish("iot", "TelemetryAnomalyRecordUpdated", saved.getId(), TelemetryAnomalyRecordResponse.from(saved));
        return TelemetryAnomalyRecordResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        TelemetryAnomalyRecord entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("iot", "TelemetryAnomalyRecordDeleted", id, null);
    }

    private TelemetryAnomalyRecord require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
