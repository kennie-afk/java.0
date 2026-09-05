package com.smartseason.attendance.service;

import com.smartseason.attendance.domain.ClockEvent;
import com.smartseason.attendance.platform.EventPublisher;
import com.smartseason.attendance.platform.PageResponse;
import com.smartseason.attendance.platform.ResourceNotFoundException;
import com.smartseason.attendance.platform.TenantContext;
import com.smartseason.attendance.repo.ClockEventRepository;
import com.smartseason.attendance.web.dto.ClockEventCreateRequest;
import com.smartseason.attendance.web.dto.ClockEventResponse;
import com.smartseason.attendance.web.dto.ClockEventUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClockEventService {

    private static final String RESOURCE = "ClockEvent";

    private final ClockEventRepository repository;
    private final EventPublisher events;

    public ClockEventService(ClockEventRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<ClockEventResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(ClockEventResponse::from));
    }

    public ClockEventResponse get(UUID id) {
        return ClockEventResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ClockEventResponse create(ClockEventCreateRequest request) {
        ClockEvent entity = new ClockEvent();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setWorkerId(request.workerId());
        entity.setFarmId(request.farmId());
        entity.setShiftId(request.shiftId());
        entity.setEventType(request.eventType());
        entity.setOccurredAt(request.occurredAt());
        entity.setRecordedAt(request.recordedAt());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setAccuracyM(request.accuracyM());
        entity.setGeofenceId(request.geofenceId());
        entity.setInsideGeofence(request.insideGeofence());
        entity.setBiometricScore(request.biometricScore());
        entity.setDeviceId(request.deviceId());
        entity.setMockLocation(request.mockLocation());
        entity.setOfflineSynced(request.offlineSynced());
        entity.setClientEventId(request.clientEventId());
        entity.setVerdict(request.verdict());
        entity.setFlagReason(request.flagReason());

        ClockEvent saved = repository.save(entity);
        events.publish("workforce", "ClockEventCreated", saved.getId(), ClockEventResponse.from(saved));
        return ClockEventResponse.from(saved);
    }

    @Transactional
    public ClockEventResponse update(UUID id, ClockEventUpdateRequest request) {
        ClockEvent entity = require(id);
        if (request.workerId() != null) {
            entity.setWorkerId(request.workerId());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.shiftId() != null) {
            entity.setShiftId(request.shiftId());
        }
        if (request.eventType() != null) {
            entity.setEventType(request.eventType());
        }
        if (request.occurredAt() != null) {
            entity.setOccurredAt(request.occurredAt());
        }
        if (request.recordedAt() != null) {
            entity.setRecordedAt(request.recordedAt());
        }
        if (request.latitude() != null) {
            entity.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            entity.setLongitude(request.longitude());
        }
        if (request.accuracyM() != null) {
            entity.setAccuracyM(request.accuracyM());
        }
        if (request.geofenceId() != null) {
            entity.setGeofenceId(request.geofenceId());
        }
        if (request.insideGeofence() != null) {
            entity.setInsideGeofence(request.insideGeofence());
        }
        if (request.biometricScore() != null) {
            entity.setBiometricScore(request.biometricScore());
        }
        if (request.deviceId() != null) {
            entity.setDeviceId(request.deviceId());
        }
        if (request.mockLocation() != null) {
            entity.setMockLocation(request.mockLocation());
        }
        if (request.offlineSynced() != null) {
            entity.setOfflineSynced(request.offlineSynced());
        }
        if (request.clientEventId() != null) {
            entity.setClientEventId(request.clientEventId());
        }
        if (request.verdict() != null) {
            entity.setVerdict(request.verdict());
        }
        if (request.flagReason() != null) {
            entity.setFlagReason(request.flagReason());
        }

        ClockEvent saved = repository.save(entity);
        events.publish("workforce", "ClockEventUpdated", saved.getId(), ClockEventResponse.from(saved));
        return ClockEventResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        ClockEvent entity = require(id);
        repository.delete(entity);
        events.publish("workforce", "ClockEventDeleted", id, null);
    }

    private ClockEvent require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
