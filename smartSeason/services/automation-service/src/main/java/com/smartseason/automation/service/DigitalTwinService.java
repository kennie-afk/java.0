package com.smartseason.automation.service;

import com.smartseason.automation.domain.DigitalTwin;
import com.smartseason.automation.platform.CountCache;
import com.smartseason.automation.platform.CountCache;
import com.smartseason.automation.platform.EventPublisher;
import com.smartseason.automation.platform.ReferenceChecker;
import com.smartseason.automation.platform.Cursor;
import com.smartseason.automation.platform.CursorPage;
import com.smartseason.automation.platform.PageResponse;
import com.smartseason.automation.platform.ResourceNotFoundException;
import com.smartseason.automation.platform.TenantContext;
import com.smartseason.automation.repo.DigitalTwinRepository;
import com.smartseason.automation.web.dto.DigitalTwinCreateRequest;
import com.smartseason.automation.web.dto.DigitalTwinResponse;
import com.smartseason.automation.web.dto.DigitalTwinUpdateRequest;
import com.smartseason.automation.platform.ListFilter;
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
public class DigitalTwinService {

    private static final String RESOURCE = "DigitalTwin";
    private static final String ENTITY = "digital_twins";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("plotId", UUID.class),
            Map.entry("irrigationState", DigitalTwin.IrrigationState.class));

    private static final List<String> SEARCHABLE = List.of();

    private final DigitalTwinRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public DigitalTwinService(DigitalTwinRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<DigitalTwinResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<DigitalTwin>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(DigitalTwinResponse::from));
    }

    public PageResponse<DigitalTwinResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(DigitalTwinResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<DigitalTwinResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<DigitalTwin> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(DigitalTwinResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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
        counts.invalidate(ENTITY, saved.getTenantId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("iot", "DigitalTwinDeleted", id, null);
    }

    private DigitalTwin require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
