package com.smartseason.automation.service;

import com.smartseason.automation.domain.AutomationRule;
import com.smartseason.automation.platform.EventPublisher;
import com.smartseason.automation.platform.PageResponse;
import com.smartseason.automation.platform.ResourceNotFoundException;
import com.smartseason.automation.platform.TenantContext;
import com.smartseason.automation.repo.AutomationRuleRepository;
import com.smartseason.automation.web.dto.AutomationRuleCreateRequest;
import com.smartseason.automation.web.dto.AutomationRuleResponse;
import com.smartseason.automation.web.dto.AutomationRuleUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AutomationRuleService {

    private static final String RESOURCE = "AutomationRule";

    private final AutomationRuleRepository repository;
    private final EventPublisher events;

    public AutomationRuleService(AutomationRuleRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<AutomationRuleResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(AutomationRuleResponse::from));
    }

    public AutomationRuleResponse get(UUID id) {
        return AutomationRuleResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public AutomationRuleResponse create(AutomationRuleCreateRequest request) {
        AutomationRule entity = new AutomationRule();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setName(request.name());
        entity.setPlotId(request.plotId());
        entity.setTriggerMetric(request.triggerMetric());
        entity.setOperator(request.operator());
        entity.setThreshold(request.threshold());
        entity.setActionType(request.actionType());
        entity.setActionTargetDeviceId(request.actionTargetDeviceId());
        entity.setDurationSeconds(request.durationSeconds());
        entity.setCooldownSeconds(request.cooldownSeconds());
        entity.setEnabled(request.enabled());
        entity.setLastTriggeredAt(request.lastTriggeredAt());

        AutomationRule saved = repository.save(entity);
        events.publish("iot", "AutomationRuleCreated", saved.getId(), AutomationRuleResponse.from(saved));
        return AutomationRuleResponse.from(saved);
    }

    @Transactional
    public AutomationRuleResponse update(UUID id, AutomationRuleUpdateRequest request) {
        AutomationRule entity = require(id);
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.triggerMetric() != null) {
            entity.setTriggerMetric(request.triggerMetric());
        }
        if (request.operator() != null) {
            entity.setOperator(request.operator());
        }
        if (request.threshold() != null) {
            entity.setThreshold(request.threshold());
        }
        if (request.actionType() != null) {
            entity.setActionType(request.actionType());
        }
        if (request.actionTargetDeviceId() != null) {
            entity.setActionTargetDeviceId(request.actionTargetDeviceId());
        }
        if (request.durationSeconds() != null) {
            entity.setDurationSeconds(request.durationSeconds());
        }
        if (request.cooldownSeconds() != null) {
            entity.setCooldownSeconds(request.cooldownSeconds());
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        if (request.lastTriggeredAt() != null) {
            entity.setLastTriggeredAt(request.lastTriggeredAt());
        }

        AutomationRule saved = repository.save(entity);
        events.publish("iot", "AutomationRuleUpdated", saved.getId(), AutomationRuleResponse.from(saved));
        return AutomationRuleResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        AutomationRule entity = require(id);
        repository.delete(entity);
        events.publish("iot", "AutomationRuleDeleted", id, null);
    }

    private AutomationRule require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
