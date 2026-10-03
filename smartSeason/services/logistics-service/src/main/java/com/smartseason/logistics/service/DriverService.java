package com.smartseason.logistics.service;

import com.smartseason.logistics.domain.Driver;
import com.smartseason.logistics.platform.CountCache;
import com.smartseason.logistics.platform.CountCache;
import com.smartseason.logistics.platform.EventPublisher;
import com.smartseason.logistics.platform.ReferenceChecker;
import com.smartseason.logistics.platform.Cursor;
import com.smartseason.logistics.platform.CursorPage;
import com.smartseason.logistics.platform.PageResponse;
import com.smartseason.logistics.platform.ResourceNotFoundException;
import com.smartseason.logistics.platform.TenantContext;
import com.smartseason.logistics.repo.DriverRepository;
import com.smartseason.logistics.web.dto.DriverCreateRequest;
import com.smartseason.logistics.web.dto.DriverResponse;
import com.smartseason.logistics.web.dto.DriverUpdateRequest;
import com.smartseason.logistics.platform.ListFilter;
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
public class DriverService {

    private static final String RESOURCE = "Driver";
    private static final String ENTITY = "drivers";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("userId", UUID.class),
            Map.entry("fullName", String.class),
            Map.entry("phone", String.class),
            Map.entry("licenceNumber", String.class),
            Map.entry("assignedVehicleId", UUID.class),
            Map.entry("status", Driver.Status.class));

    private static final List<String> SEARCHABLE = List.of("fullName", "phone", "licenceNumber");

    private final DriverRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public DriverService(DriverRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<DriverResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<Driver>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(DriverResponse::from));
    }

    public PageResponse<DriverResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(DriverResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<DriverResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<Driver> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(DriverResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public DriverResponse get(UUID id) {
        return DriverResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public DriverResponse create(DriverCreateRequest request) {
        Driver entity = new Driver();
        entity.setTenantId(TenantContext.requireTenantId());

        references.require("Vehicle", "assignedVehicleId", request.assignedVehicleId());
        entity.setUserId(request.userId());
        entity.setFullName(request.fullName());
        entity.setPhone(request.phone());
        entity.setLicenceNumber(request.licenceNumber());
        entity.setLicenceExpiry(request.licenceExpiry());
        entity.setAssignedVehicleId(request.assignedVehicleId());
        entity.setRating(request.rating());
        entity.setStatus(request.status());

        Driver saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "DriverCreated", saved.getId(), DriverResponse.from(saved));
        return DriverResponse.from(saved);
    }

    @Transactional
    public DriverResponse update(UUID id, DriverUpdateRequest request) {
        Driver entity = require(id);
        references.require("Vehicle", "assignedVehicleId", request.assignedVehicleId());
        if (request.userId() != null) {
            entity.setUserId(request.userId());
        }
        if (request.fullName() != null) {
            entity.setFullName(request.fullName());
        }
        if (request.phone() != null) {
            entity.setPhone(request.phone());
        }
        if (request.licenceNumber() != null) {
            entity.setLicenceNumber(request.licenceNumber());
        }
        if (request.licenceExpiry() != null) {
            entity.setLicenceExpiry(request.licenceExpiry());
        }
        if (request.assignedVehicleId() != null) {
            entity.setAssignedVehicleId(request.assignedVehicleId());
        }
        if (request.rating() != null) {
            entity.setRating(request.rating());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        Driver saved = repository.save(entity);
        events.publish("market", "DriverUpdated", saved.getId(), DriverResponse.from(saved));
        return DriverResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Driver entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "DriverDeleted", id, null);
    }

    private Driver require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
