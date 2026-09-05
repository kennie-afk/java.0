package com.smartseason.fraud.service;

import com.smartseason.fraud.domain.FraudRule;
import com.smartseason.fraud.platform.EventPublisher;
import com.smartseason.fraud.platform.PageResponse;
import com.smartseason.fraud.platform.ResourceNotFoundException;
import com.smartseason.fraud.platform.TenantContext;
import com.smartseason.fraud.repo.FraudRuleRepository;
import com.smartseason.fraud.web.dto.FraudRuleCreateRequest;
import com.smartseason.fraud.web.dto.FraudRuleResponse;
import com.smartseason.fraud.web.dto.FraudRuleUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FraudRuleService {

    private static final String RESOURCE = "FraudRule";

    private final FraudRuleRepository repository;
    private final EventPublisher events;

    public FraudRuleService(FraudRuleRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<FraudRuleResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(FraudRuleResponse::from));
    }

    public FraudRuleResponse get(UUID id) {
        return FraudRuleResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public FraudRuleResponse create(FraudRuleCreateRequest request) {
        FraudRule entity = new FraudRule();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCode(request.code());
        entity.setTypology(request.typology());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setExpression(request.expression());
        entity.setThreshold(request.threshold());
        entity.setSeverity(request.severity());
        entity.setWeight(request.weight());
        entity.setEnabled(request.enabled());
        entity.setAutoHoldPayout(request.autoHoldPayout());

        FraudRule saved = repository.save(entity);
        events.publish("workforce", "FraudRuleCreated", saved.getId(), FraudRuleResponse.from(saved));
        return FraudRuleResponse.from(saved);
    }

    @Transactional
    public FraudRuleResponse update(UUID id, FraudRuleUpdateRequest request) {
        FraudRule entity = require(id);
        if (request.code() != null) {
            entity.setCode(request.code());
        }
        if (request.typology() != null) {
            entity.setTypology(request.typology());
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.expression() != null) {
            entity.setExpression(request.expression());
        }
        if (request.threshold() != null) {
            entity.setThreshold(request.threshold());
        }
        if (request.severity() != null) {
            entity.setSeverity(request.severity());
        }
        if (request.weight() != null) {
            entity.setWeight(request.weight());
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        if (request.autoHoldPayout() != null) {
            entity.setAutoHoldPayout(request.autoHoldPayout());
        }

        FraudRule saved = repository.save(entity);
        events.publish("workforce", "FraudRuleUpdated", saved.getId(), FraudRuleResponse.from(saved));
        return FraudRuleResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        FraudRule entity = require(id);
        repository.delete(entity);
        events.publish("workforce", "FraudRuleDeleted", id, null);
    }

    private FraudRule require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
