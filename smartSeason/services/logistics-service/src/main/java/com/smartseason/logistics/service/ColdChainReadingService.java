package com.smartseason.logistics.service;

import com.smartseason.logistics.domain.ColdChainReading;
import com.smartseason.logistics.platform.EventPublisher;
import com.smartseason.logistics.platform.PageResponse;
import com.smartseason.logistics.platform.ResourceNotFoundException;
import com.smartseason.logistics.platform.TenantContext;
import com.smartseason.logistics.repo.ColdChainReadingRepository;
import com.smartseason.logistics.web.dto.ColdChainReadingCreateRequest;
import com.smartseason.logistics.web.dto.ColdChainReadingResponse;
import com.smartseason.logistics.web.dto.ColdChainReadingUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ColdChainReadingService {

    private static final String RESOURCE = "ColdChainReading";

    private final ColdChainReadingRepository repository;
    private final EventPublisher events;

    public ColdChainReadingService(ColdChainReadingRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<ColdChainReadingResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(ColdChainReadingResponse::from));
    }

    public ColdChainReadingResponse get(UUID id) {
        return ColdChainReadingResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ColdChainReadingResponse create(ColdChainReadingCreateRequest request) {
        ColdChainReading entity = new ColdChainReading();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setTransportJobId(request.transportJobId());
        entity.setRecordedAt(request.recordedAt());
        entity.setTemperatureC(request.temperatureC());
        entity.setHumidityPct(request.humidityPct());
        entity.setDeviceId(request.deviceId());
        entity.setBreach(request.breach());

        ColdChainReading saved = repository.save(entity);
        events.publish("market", "ColdChainReadingCreated", saved.getId(), ColdChainReadingResponse.from(saved));
        return ColdChainReadingResponse.from(saved);
    }

    @Transactional
    public ColdChainReadingResponse update(UUID id, ColdChainReadingUpdateRequest request) {
        ColdChainReading entity = require(id);
        if (request.transportJobId() != null) {
            entity.setTransportJobId(request.transportJobId());
        }
        if (request.recordedAt() != null) {
            entity.setRecordedAt(request.recordedAt());
        }
        if (request.temperatureC() != null) {
            entity.setTemperatureC(request.temperatureC());
        }
        if (request.humidityPct() != null) {
            entity.setHumidityPct(request.humidityPct());
        }
        if (request.deviceId() != null) {
            entity.setDeviceId(request.deviceId());
        }
        if (request.breach() != null) {
            entity.setBreach(request.breach());
        }

        ColdChainReading saved = repository.save(entity);
        events.publish("market", "ColdChainReadingUpdated", saved.getId(), ColdChainReadingResponse.from(saved));
        return ColdChainReadingResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        ColdChainReading entity = require(id);
        repository.delete(entity);
        events.publish("market", "ColdChainReadingDeleted", id, null);
    }

    private ColdChainReading require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
