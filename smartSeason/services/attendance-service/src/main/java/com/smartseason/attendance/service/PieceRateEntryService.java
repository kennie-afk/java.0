package com.smartseason.attendance.service;

import com.smartseason.attendance.domain.PieceRateEntry;
import com.smartseason.attendance.platform.EventPublisher;
import com.smartseason.attendance.platform.PageResponse;
import com.smartseason.attendance.platform.ResourceNotFoundException;
import com.smartseason.attendance.platform.TenantContext;
import com.smartseason.attendance.repo.PieceRateEntryRepository;
import com.smartseason.attendance.web.dto.PieceRateEntryCreateRequest;
import com.smartseason.attendance.web.dto.PieceRateEntryResponse;
import com.smartseason.attendance.web.dto.PieceRateEntryUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PieceRateEntryService {

    private static final String RESOURCE = "PieceRateEntry";

    private final PieceRateEntryRepository repository;
    private final EventPublisher events;

    public PieceRateEntryService(PieceRateEntryRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<PieceRateEntryResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(PieceRateEntryResponse::from));
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
        events.publish("workforce", "PieceRateEntryCreated", saved.getId(), PieceRateEntryResponse.from(saved));
        return PieceRateEntryResponse.from(saved);
    }

    @Transactional
    public PieceRateEntryResponse update(UUID id, PieceRateEntryUpdateRequest request) {
        PieceRateEntry entity = require(id);
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
        events.publish("workforce", "PieceRateEntryDeleted", id, null);
    }

    private PieceRateEntry require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
