package com.smartseason.automation.service;

import com.smartseason.automation.domain.SafetyInterlock;
import com.smartseason.automation.platform.EventPublisher;
import com.smartseason.automation.platform.PageResponse;
import com.smartseason.automation.platform.ResourceNotFoundException;
import com.smartseason.automation.platform.TenantContext;
import com.smartseason.automation.repo.SafetyInterlockRepository;
import com.smartseason.automation.web.dto.SafetyInterlockCreateRequest;
import com.smartseason.automation.web.dto.SafetyInterlockResponse;
import com.smartseason.automation.web.dto.SafetyInterlockUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SafetyInterlockService {

    private static final String RESOURCE = "SafetyInterlock";

    private final SafetyInterlockRepository repository;
    private final EventPublisher events;

    public SafetyInterlockService(SafetyInterlockRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<SafetyInterlockResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(SafetyInterlockResponse::from));
    }

    public SafetyInterlockResponse get(UUID id) {
        return SafetyInterlockResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public SafetyInterlockResponse create(SafetyInterlockCreateRequest request) {
        SafetyInterlock entity = new SafetyInterlock();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setDeviceId(request.deviceId());
        entity.setInterlockType(request.interlockType());
        entity.setMaxRuntimeSeconds(request.maxRuntimeSeconds());
        entity.setConflictingDeviceId(request.conflictingDeviceId());
        entity.setEngaged(request.engaged());
        entity.setEngagedAt(request.engagedAt());
        entity.setReason(request.reason());

        SafetyInterlock saved = repository.save(entity);
        events.publish("iot", "SafetyInterlockCreated", saved.getId(), SafetyInterlockResponse.from(saved));
        return SafetyInterlockResponse.from(saved);
    }

    @Transactional
    public SafetyInterlockResponse update(UUID id, SafetyInterlockUpdateRequest request) {
        SafetyInterlock entity = require(id);
        if (request.deviceId() != null) {
            entity.setDeviceId(request.deviceId());
        }
        if (request.interlockType() != null) {
            entity.setInterlockType(request.interlockType());
        }
        if (request.maxRuntimeSeconds() != null) {
            entity.setMaxRuntimeSeconds(request.maxRuntimeSeconds());
        }
        if (request.conflictingDeviceId() != null) {
            entity.setConflictingDeviceId(request.conflictingDeviceId());
        }
        if (request.engaged() != null) {
            entity.setEngaged(request.engaged());
        }
        if (request.engagedAt() != null) {
            entity.setEngagedAt(request.engagedAt());
        }
        if (request.reason() != null) {
            entity.setReason(request.reason());
        }

        SafetyInterlock saved = repository.save(entity);
        events.publish("iot", "SafetyInterlockUpdated", saved.getId(), SafetyInterlockResponse.from(saved));
        return SafetyInterlockResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        SafetyInterlock entity = require(id);
        repository.delete(entity);
        events.publish("iot", "SafetyInterlockDeleted", id, null);
    }

    private SafetyInterlock require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
