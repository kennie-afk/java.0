package com.smartseason.attendance.service;

import com.smartseason.attendance.domain.PieceRateEntry;
import com.smartseason.attendance.platform.CountCache;
import com.smartseason.attendance.platform.CountCache;
import com.smartseason.attendance.platform.EventPublisher;
import com.smartseason.attendance.platform.ReferenceChecker;
import com.smartseason.attendance.platform.Cursor;
import com.smartseason.attendance.platform.CursorPage;
import com.smartseason.attendance.platform.PageResponse;
import com.smartseason.attendance.platform.ResourceNotFoundException;
import com.smartseason.attendance.platform.TenantContext;
import com.smartseason.attendance.repo.PieceRateEntryRepository;
import com.smartseason.attendance.web.dto.PieceRateEntryCreateRequest;
import com.smartseason.attendance.web.dto.PieceRateEntryResponse;
import com.smartseason.attendance.web.dto.PieceRateEntryUpdateRequest;
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
public class PieceRateEntryService {

    private static final String RESOURCE = "PieceRateEntry";
    private static final String ENTITY = "piece_rate_entries";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("workerId", UUID.class),
            Map.entry("shiftId", UUID.class),
            Map.entry("farmId", UUID.class),
            Map.entry("plotId", UUID.class),
            Map.entry("taskCode", String.class),
            Map.entry("unit", String.class),
            Map.entry("recordedBy", UUID.class),
            Map.entry("weighStationId", String.class),
            Map.entry("verifiedBy", UUID.class),
            Map.entry("status", PieceRateEntry.Status.class));

    private static final List<String> SEARCHABLE = List.of("taskCode", "unit", "weighStationId");

    private final PieceRateEntryRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public PieceRateEntryService(PieceRateEntryRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<PieceRateEntryResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<PieceRateEntry>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(PieceRateEntryResponse::from));
    }

    public PageResponse<PieceRateEntryResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(PieceRateEntryResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<PieceRateEntryResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<PieceRateEntry> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(PieceRateEntryResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public PieceRateEntryResponse get(UUID id) {
        return PieceRateEntryResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public PieceRateEntryResponse create(PieceRateEntryCreateRequest request) {
        PieceRateEntry entity = new PieceRateEntry();
        entity.setTenantId(TenantContext.requireTenantId());

        references.require("Shift", "shiftId", request.shiftId());
        entity.setWorkerId(request.workerId());
        entity.setShiftId(request.shiftId());
        entity.setFarmId(request.farmId());
        entity.setPlotId(request.plotId());
        entity.setTaskCode(request.taskCode());
        entity.setQuantity(request.quantity());
        entity.setUnit(request.unit());
        entity.setRecordedAt(request.recordedAt());
        entity.setRecordedBy(request.recordedBy());
        entity.setWeighStationId(request.weighStationId());
        entity.setVerifiedBy(request.verifiedBy());
        entity.setVerifiedAt(request.verifiedAt());
        entity.setStatus(request.status());

        PieceRateEntry saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("workforce", "PieceRateEntryCreated", saved.getId(), PieceRateEntryResponse.from(saved));
        return PieceRateEntryResponse.from(saved);
    }

    @Transactional
    public PieceRateEntryResponse update(UUID id, PieceRateEntryUpdateRequest request) {
        PieceRateEntry entity = require(id);
        references.require("Shift", "shiftId", request.shiftId());
        if (request.workerId() != null) {
            entity.setWorkerId(request.workerId());
        }
        if (request.shiftId() != null) {
            entity.setShiftId(request.shiftId());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.taskCode() != null) {
            entity.setTaskCode(request.taskCode());
        }
        if (request.quantity() != null) {
            entity.setQuantity(request.quantity());
        }
        if (request.unit() != null) {
            entity.setUnit(request.unit());
        }
        if (request.recordedAt() != null) {
            entity.setRecordedAt(request.recordedAt());
        }
        if (request.recordedBy() != null) {
            entity.setRecordedBy(request.recordedBy());
        }
        if (request.weighStationId() != null) {
            entity.setWeighStationId(request.weighStationId());
        }
        if (request.verifiedBy() != null) {
            entity.setVerifiedBy(request.verifiedBy());
        }
        if (request.verifiedAt() != null) {
            entity.setVerifiedAt(request.verifiedAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        PieceRateEntry saved = repository.save(entity);
        events.publish("workforce", "PieceRateEntryUpdated", saved.getId(), PieceRateEntryResponse.from(saved));
        return PieceRateEntryResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        PieceRateEntry entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("workforce", "PieceRateEntryDeleted", id, null);
    }

    private PieceRateEntry require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
