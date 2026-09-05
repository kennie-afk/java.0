package com.smartseason.deviceregistry.service;

import com.smartseason.deviceregistry.domain.Device;
import com.smartseason.deviceregistry.platform.EventPublisher;
import com.smartseason.deviceregistry.platform.PageResponse;
import com.smartseason.deviceregistry.platform.ResourceNotFoundException;
import com.smartseason.deviceregistry.platform.TenantContext;
import com.smartseason.deviceregistry.repo.DeviceRepository;
import com.smartseason.deviceregistry.web.dto.DeviceCreateRequest;
import com.smartseason.deviceregistry.web.dto.DeviceResponse;
import com.smartseason.deviceregistry.web.dto.DeviceUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DeviceService {

    private static final String RESOURCE = "Device";

    private final DeviceRepository repository;
    private final EventPublisher events;

    public DeviceService(DeviceRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<DeviceResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(DeviceResponse::from));
    }

    public DeviceResponse get(UUID id) {
        return DeviceResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public DeviceResponse create(DeviceCreateRequest request) {
        Device entity = new Device();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setSerialNumber(request.serialNumber());
        entity.setDeviceType(request.deviceType());
        entity.setPlotId(request.plotId());
        entity.setFarmId(request.farmId());
        entity.setModel(request.model());
        entity.setFirmwareVersion(request.firmwareVersion());
        entity.setStatus(request.status());
        entity.setLastSeenAt(request.lastSeenAt());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());

        Device saved = repository.save(entity);
        events.publish("iot", "DeviceCreated", saved.getId(), DeviceResponse.from(saved));
        return DeviceResponse.from(saved);
    }

    @Transactional
    public DeviceResponse update(UUID id, DeviceUpdateRequest request) {
        Device entity = require(id);
        if (request.serialNumber() != null) {
            entity.setSerialNumber(request.serialNumber());
        }
        if (request.deviceType() != null) {
            entity.setDeviceType(request.deviceType());
        }
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.model() != null) {
            entity.setModel(request.model());
        }
        if (request.firmwareVersion() != null) {
            entity.setFirmwareVersion(request.firmwareVersion());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.lastSeenAt() != null) {
            entity.setLastSeenAt(request.lastSeenAt());
        }
        if (request.latitude() != null) {
            entity.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            entity.setLongitude(request.longitude());
        }

        Device saved = repository.save(entity);
        events.publish("iot", "DeviceUpdated", saved.getId(), DeviceResponse.from(saved));
        return DeviceResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Device entity = require(id);
        repository.delete(entity);
        events.publish("iot", "DeviceDeleted", id, null);
    }

    private Device require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
