package com.smartseason.automation.service;

import com.smartseason.automation.domain.DigitalTwin;
import com.smartseason.automation.platform.EventPublisher;
import com.smartseason.automation.platform.PageResponse;
import com.smartseason.automation.platform.ResourceNotFoundException;
import com.smartseason.automation.platform.TenantContext;
import com.smartseason.automation.repo.DigitalTwinRepository;
import com.smartseason.automation.web.dto.DigitalTwinCreateRequest;
import com.smartseason.automation.web.dto.DigitalTwinResponse;
import com.smartseason.automation.web.dto.DigitalTwinUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DigitalTwinService {

    private static final String RESOURCE = "DigitalTwin";

    private final DigitalTwinRepository repository;
    private final EventPublisher events;

    public DigitalTwinService(DigitalTwinRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<DigitalTwinResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(DigitalTwinResponse::from));
    }

    public DigitalTwinResponse get(UUID id) {
        return DigitalTwinResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public DigitalTwinResponse create(DigitalTwinCreateRequest request) {
        DigitalTwin entity = new DigitalTwin();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setPlotId(request.plotId());
        entity.setSoilMoisturePct(request.soilMoisturePct());
        entity.setSoilTempC(request.soilTempC());
        entity.setCanopyIndex(request.canopyIndex());
        entity.setIrrigationState(request.irrigationState());
        entity.setLastIrrigatedAt(request.lastIrrigatedAt());
        entity.setUpdatedFromEventAt(request.updatedFromEventAt());
        entity.setState(request.state());

        DigitalTwin saved = repository.save(entity);
        events.publish("iot", "DigitalTwinCreated", saved.getId(), DigitalTwinResponse.from(saved));
        return DigitalTwinResponse.from(saved);
    }

    @Transactional
    public DigitalTwinResponse update(UUID id, DigitalTwinUpdateRequest request) {
        DigitalTwin entity = require(id);
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.soilMoisturePct() != null) {
            entity.setSoilMoisturePct(request.soilMoisturePct());
        }
        if (request.soilTempC() != null) {
            entity.setSoilTempC(request.soilTempC());
        }
        if (request.canopyIndex() != null) {
            entity.setCanopyIndex(request.canopyIndex());
        }
        if (request.irrigationState() != null) {
            entity.setIrrigationState(request.irrigationState());
        }
        if (request.lastIrrigatedAt() != null) {
            entity.setLastIrrigatedAt(request.lastIrrigatedAt());
        }
        if (request.updatedFromEventAt() != null) {
            entity.setUpdatedFromEventAt(request.updatedFromEventAt());
        }
        if (request.state() != null) {
            entity.setState(request.state());
        }

        DigitalTwin saved = repository.save(entity);
        events.publish("iot", "DigitalTwinUpdated", saved.getId(), DigitalTwinResponse.from(saved));
        return DigitalTwinResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        DigitalTwin entity = require(id);
        repository.delete(entity);
        events.publish("iot", "DigitalTwinDeleted", id, null);
    }

    private DigitalTwin require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
