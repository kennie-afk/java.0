package com.smartseason.telemetryingest.service;

import com.smartseason.telemetryingest.domain.TelemetryReading;
import com.smartseason.telemetryingest.platform.CountCache;
import com.smartseason.telemetryingest.platform.CountCache;
import com.smartseason.telemetryingest.platform.EventPublisher;
import com.smartseason.telemetryingest.platform.Cursor;
import com.smartseason.telemetryingest.platform.CursorPage;
import com.smartseason.telemetryingest.platform.PageResponse;
import com.smartseason.telemetryingest.platform.ResourceNotFoundException;
import com.smartseason.telemetryingest.platform.TenantContext;
import com.smartseason.telemetryingest.repo.TelemetryReadingRepository;
import com.smartseason.telemetryingest.web.dto.TelemetryReadingCreateRequest;
import com.smartseason.telemetryingest.web.dto.TelemetryReadingResponse;
import com.smartseason.telemetryingest.web.dto.TelemetryReadingUpdateRequest;
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
public class TelemetryReadingService {

    private static final String RESOURCE = "TelemetryReading";
    private static final String ENTITY = "telemetry_readings";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("deviceId", UUID.class),
            Map.entry("plotId", UUID.class),
            Map.entry("metric", String.class),
            Map.entry("unit", String.class),
            Map.entry("quality", TelemetryReading.Quality.class));

    private static final List<String> SEARCHABLE = List.of("metric", "unit");

    private final TelemetryReadingRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public TelemetryReadingService(TelemetryReadingRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<TelemetryReadingResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<TelemetryReading>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(TelemetryReadingResponse::from));
    }

    public PageResponse<TelemetryReadingResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(TelemetryReadingResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<TelemetryReadingResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<TelemetryReading> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(TelemetryReadingResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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
        counts.invalidate(ENTITY, saved.getTenantId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("iot", "TelemetryReadingDeleted", id, null);
    }

    private TelemetryReading require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
