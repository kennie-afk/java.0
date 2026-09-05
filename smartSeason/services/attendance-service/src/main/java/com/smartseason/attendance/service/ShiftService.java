package com.smartseason.attendance.service;

import com.smartseason.attendance.domain.Shift;
import com.smartseason.attendance.platform.EventPublisher;
import com.smartseason.attendance.platform.PageResponse;
import com.smartseason.attendance.platform.ResourceNotFoundException;
import com.smartseason.attendance.platform.TenantContext;
import com.smartseason.attendance.repo.ShiftRepository;
import com.smartseason.attendance.web.dto.ShiftCreateRequest;
import com.smartseason.attendance.web.dto.ShiftResponse;
import com.smartseason.attendance.web.dto.ShiftUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ShiftService {

    private static final String RESOURCE = "Shift";

    private final ShiftRepository repository;
    private final EventPublisher events;

    public ShiftService(ShiftRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<ShiftResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(ShiftResponse::from));
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
        events.publish("workforce", "ShiftDeleted", id, null);
    }

    private Shift require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
