package com.smartseason.attendance.service;

import com.smartseason.attendance.domain.Shift;
import com.smartseason.attendance.platform.CountCache;
import com.smartseason.attendance.platform.CountCache;
import com.smartseason.attendance.platform.EventPublisher;
import com.smartseason.attendance.platform.ReferenceChecker;
import com.smartseason.attendance.platform.Cursor;
import com.smartseason.attendance.platform.CursorPage;
import com.smartseason.attendance.platform.PageResponse;
import com.smartseason.attendance.platform.ResourceNotFoundException;
import com.smartseason.attendance.platform.TenantContext;
import com.smartseason.attendance.repo.ShiftRepository;
import com.smartseason.attendance.web.dto.ShiftCreateRequest;
import com.smartseason.attendance.web.dto.ShiftResponse;
import com.smartseason.attendance.web.dto.ShiftUpdateRequest;
import com.smartseason.attendance.platform.ListFilter;
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
public class ShiftService {

    private static final String RESOURCE = "Shift";
    private static final String ENTITY = "shifts";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("workerId", UUID.class),
            Map.entry("farmId", UUID.class),
            Map.entry("gangId", UUID.class),
            Map.entry("supervisorId", UUID.class),
            Map.entry("status", Shift.Status.class),
            Map.entry("anomalyFlags", String.class));

    private static final List<String> SEARCHABLE = List.of("anomalyFlags");

    private final ShiftRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public ShiftService(ShiftRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<ShiftResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<Shift>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(ShiftResponse::from));
    }

    public PageResponse<ShiftResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(ShiftResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<ShiftResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<Shift> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(ShiftResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public ShiftResponse get(UUID id) {
        return ShiftResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ShiftResponse create(ShiftCreateRequest request) {
        Shift entity = new Shift();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setWorkerId(request.workerId());
        entity.setFarmId(request.farmId());
        entity.setGangId(request.gangId());
        entity.setStartedAt(request.startedAt());
        entity.setEndedAt(request.endedAt());
        entity.setDurationMinutes(request.durationMinutes());
        entity.setBreakMinutes(request.breakMinutes());
        entity.setSupervisorId(request.supervisorId());
        entity.setStatus(request.status());
        entity.setAnomalyFlags(request.anomalyFlags());

        Shift saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("workforce", "ShiftCreated", saved.getId(), ShiftResponse.from(saved));
        return ShiftResponse.from(saved);
    }

    @Transactional
    public ShiftResponse update(UUID id, ShiftUpdateRequest request) {
        Shift entity = require(id);
        if (request.workerId() != null) {
            entity.setWorkerId(request.workerId());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.gangId() != null) {
            entity.setGangId(request.gangId());
        }
        if (request.startedAt() != null) {
            entity.setStartedAt(request.startedAt());
        }
        if (request.endedAt() != null) {
            entity.setEndedAt(request.endedAt());
        }
        if (request.durationMinutes() != null) {
            entity.setDurationMinutes(request.durationMinutes());
        }
        if (request.breakMinutes() != null) {
            entity.setBreakMinutes(request.breakMinutes());
        }
        if (request.supervisorId() != null) {
            entity.setSupervisorId(request.supervisorId());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.anomalyFlags() != null) {
            entity.setAnomalyFlags(request.anomalyFlags());
        }

        Shift saved = repository.save(entity);
        events.publish("workforce", "ShiftUpdated", saved.getId(), ShiftResponse.from(saved));
        return ShiftResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Shift entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("workforce", "ShiftDeleted", id, null);
    }

    private Shift require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
