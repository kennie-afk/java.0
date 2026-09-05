package com.smartseason.weather.service;

import com.smartseason.weather.domain.NdviReading;
import com.smartseason.weather.platform.EventPublisher;
import com.smartseason.weather.platform.PageResponse;
import com.smartseason.weather.platform.ResourceNotFoundException;
import com.smartseason.weather.platform.TenantContext;
import com.smartseason.weather.repo.NdviReadingRepository;
import com.smartseason.weather.web.dto.NdviReadingCreateRequest;
import com.smartseason.weather.web.dto.NdviReadingResponse;
import com.smartseason.weather.web.dto.NdviReadingUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NdviReadingService {

    private static final String RESOURCE = "NdviReading";

    private final NdviReadingRepository repository;
    private final EventPublisher events;

    public NdviReadingService(NdviReadingRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<NdviReadingResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(NdviReadingResponse::from));
    }

    public NdviReadingResponse get(UUID id) {
        return NdviReadingResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public NdviReadingResponse create(NdviReadingCreateRequest request) {
        NdviReading entity = new NdviReading();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setPlotId(request.plotId());
        entity.setGeoCell(request.geoCell());
        entity.setCapturedOn(request.capturedOn());
        entity.setNdvi(request.ndvi());
        entity.setCloudCoverPct(request.cloudCoverPct());
        entity.setSatellite(request.satellite());
        entity.setTileUrl(request.tileUrl());

        NdviReading saved = repository.save(entity);
        events.publish("farm", "NdviReadingCreated", saved.getId(), NdviReadingResponse.from(saved));
        return NdviReadingResponse.from(saved);
    }

    @Transactional
    public NdviReadingResponse update(UUID id, NdviReadingUpdateRequest request) {
        NdviReading entity = require(id);
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.geoCell() != null) {
            entity.setGeoCell(request.geoCell());
        }
        if (request.capturedOn() != null) {
            entity.setCapturedOn(request.capturedOn());
        }
        if (request.ndvi() != null) {
            entity.setNdvi(request.ndvi());
        }
        if (request.cloudCoverPct() != null) {
            entity.setCloudCoverPct(request.cloudCoverPct());
        }
        if (request.satellite() != null) {
            entity.setSatellite(request.satellite());
        }
        if (request.tileUrl() != null) {
            entity.setTileUrl(request.tileUrl());
        }

        NdviReading saved = repository.save(entity);
        events.publish("farm", "NdviReadingUpdated", saved.getId(), NdviReadingResponse.from(saved));
        return NdviReadingResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        NdviReading entity = require(id);
        repository.delete(entity);
        events.publish("farm", "NdviReadingDeleted", id, null);
    }

    private NdviReading require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
