package com.smartseason.deviceregistry.service;

import com.smartseason.deviceregistry.domain.Device;
import com.smartseason.deviceregistry.platform.CountCache;
import com.smartseason.deviceregistry.platform.CountCache;
import com.smartseason.deviceregistry.platform.EventPublisher;
import com.smartseason.deviceregistry.platform.Cursor;
import com.smartseason.deviceregistry.platform.CursorPage;
import com.smartseason.deviceregistry.platform.PageResponse;
import com.smartseason.deviceregistry.platform.ResourceNotFoundException;
import com.smartseason.deviceregistry.platform.TenantContext;
import com.smartseason.deviceregistry.repo.DeviceRepository;
import com.smartseason.deviceregistry.web.dto.DeviceCreateRequest;
import com.smartseason.deviceregistry.web.dto.DeviceResponse;
import com.smartseason.deviceregistry.web.dto.DeviceUpdateRequest;
import com.smartseason.deviceregistry.platform.ListFilter;
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
public class DeviceService {

    private static final String RESOURCE = "Device";
    private static final String ENTITY = "devices";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("serialNumber", String.class),
            Map.entry("deviceType", Device.DeviceType.class),
            Map.entry("plotId", UUID.class),
            Map.entry("farmId", UUID.class),
            Map.entry("model", String.class),
            Map.entry("firmwareVersion", String.class),
            Map.entry("status", Device.Status.class));

    private static final List<String> SEARCHABLE = List.of("serialNumber", "model", "firmwareVersion");

    private final DeviceRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public DeviceService(DeviceRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<DeviceResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<Device>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(DeviceResponse::from));
    }

    public PageResponse<DeviceResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(DeviceResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<DeviceResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<Device> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(DeviceResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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
        counts.invalidate(ENTITY, saved.getTenantId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("iot", "DeviceDeleted", id, null);
    }

    private Device require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
