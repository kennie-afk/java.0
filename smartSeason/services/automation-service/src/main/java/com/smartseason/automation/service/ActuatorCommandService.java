package com.smartseason.automation.service;

import com.smartseason.automation.domain.ActuatorCommand;
import com.smartseason.automation.platform.EventPublisher;
import com.smartseason.automation.platform.PageResponse;
import com.smartseason.automation.platform.ResourceNotFoundException;
import com.smartseason.automation.platform.TenantContext;
import com.smartseason.automation.repo.ActuatorCommandRepository;
import com.smartseason.automation.web.dto.ActuatorCommandCreateRequest;
import com.smartseason.automation.web.dto.ActuatorCommandResponse;
import com.smartseason.automation.web.dto.ActuatorCommandUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ActuatorCommandService {

    private static final String RESOURCE = "ActuatorCommand";

    private final ActuatorCommandRepository repository;
    private final EventPublisher events;

    public ActuatorCommandService(ActuatorCommandRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<ActuatorCommandResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(ActuatorCommandResponse::from));
    }

    public ActuatorCommandResponse get(UUID id) {
        return ActuatorCommandResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ActuatorCommandResponse create(ActuatorCommandCreateRequest request) {
        ActuatorCommand entity = new ActuatorCommand();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setDeviceId(request.deviceId());
        entity.setRuleId(request.ruleId());
        entity.setCommandKey(request.commandKey());
        entity.setAction(request.action());
        entity.setPayload(request.payload());
        entity.setIssuedAt(request.issuedAt());
        entity.setAcknowledgedAt(request.acknowledgedAt());
        entity.setCompletedAt(request.completedAt());
        entity.setStatus(request.status());
        entity.setAttempts(request.attempts());
        entity.setFailureReason(request.failureReason());

        ActuatorCommand saved = repository.save(entity);
        events.publish("iot", "ActuatorCommandCreated", saved.getId(), ActuatorCommandResponse.from(saved));
        return ActuatorCommandResponse.from(saved);
    }

    @Transactional
    public ActuatorCommandResponse update(UUID id, ActuatorCommandUpdateRequest request) {
        ActuatorCommand entity = require(id);
        if (request.deviceId() != null) {
            entity.setDeviceId(request.deviceId());
        }
        if (request.ruleId() != null) {
            entity.setRuleId(request.ruleId());
        }
        if (request.commandKey() != null) {
            entity.setCommandKey(request.commandKey());
        }
        if (request.action() != null) {
            entity.setAction(request.action());
        }
        if (request.payload() != null) {
            entity.setPayload(request.payload());
        }
        if (request.issuedAt() != null) {
            entity.setIssuedAt(request.issuedAt());
        }
        if (request.acknowledgedAt() != null) {
            entity.setAcknowledgedAt(request.acknowledgedAt());
        }
        if (request.completedAt() != null) {
            entity.setCompletedAt(request.completedAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.attempts() != null) {
            entity.setAttempts(request.attempts());
        }
        if (request.failureReason() != null) {
            entity.setFailureReason(request.failureReason());
        }

        ActuatorCommand saved = repository.save(entity);
        events.publish("iot", "ActuatorCommandUpdated", saved.getId(), ActuatorCommandResponse.from(saved));
        return ActuatorCommandResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        ActuatorCommand entity = require(id);
        repository.delete(entity);
        events.publish("iot", "ActuatorCommandDeleted", id, null);
    }

    private ActuatorCommand require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
