package com.smartseason.order.service;

import com.smartseason.order.domain.OrderSagaState;
import com.smartseason.order.platform.EventPublisher;
import com.smartseason.order.platform.PageResponse;
import com.smartseason.order.platform.ResourceNotFoundException;
import com.smartseason.order.platform.TenantContext;
import com.smartseason.order.repo.OrderSagaStateRepository;
import com.smartseason.order.web.dto.OrderSagaStateCreateRequest;
import com.smartseason.order.web.dto.OrderSagaStateResponse;
import com.smartseason.order.web.dto.OrderSagaStateUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderSagaStateService {

    private static final String RESOURCE = "OrderSagaState";

    private final OrderSagaStateRepository repository;
    private final EventPublisher events;

    public OrderSagaStateService(OrderSagaStateRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<OrderSagaStateResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(OrderSagaStateResponse::from));
    }

    public OrderSagaStateResponse get(UUID id) {
        return OrderSagaStateResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public OrderSagaStateResponse create(OrderSagaStateCreateRequest request) {
        OrderSagaState entity = new OrderSagaState();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setOrderId(request.orderId());
        entity.setCurrentStep(request.currentStep());
        entity.setStepStatus(request.stepStatus());
        entity.setAttempts(request.attempts());
        entity.setLastError(request.lastError());
        entity.setStartedAt(request.startedAt());
        entity.setLastTransitionAt(request.lastTransitionAt());
        entity.setCompletedAt(request.completedAt());
        entity.setContext(request.context());

        OrderSagaState saved = repository.save(entity);
        events.publish("market", "OrderSagaStateCreated", saved.getId(), OrderSagaStateResponse.from(saved));
        return OrderSagaStateResponse.from(saved);
    }

    @Transactional
    public OrderSagaStateResponse update(UUID id, OrderSagaStateUpdateRequest request) {
        OrderSagaState entity = require(id);
        if (request.orderId() != null) {
            entity.setOrderId(request.orderId());
        }
        if (request.currentStep() != null) {
            entity.setCurrentStep(request.currentStep());
        }
        if (request.stepStatus() != null) {
            entity.setStepStatus(request.stepStatus());
        }
        if (request.attempts() != null) {
            entity.setAttempts(request.attempts());
        }
        if (request.lastError() != null) {
            entity.setLastError(request.lastError());
        }
        if (request.startedAt() != null) {
            entity.setStartedAt(request.startedAt());
        }
        if (request.lastTransitionAt() != null) {
            entity.setLastTransitionAt(request.lastTransitionAt());
        }
        if (request.completedAt() != null) {
            entity.setCompletedAt(request.completedAt());
        }
        if (request.context() != null) {
            entity.setContext(request.context());
        }

        OrderSagaState saved = repository.save(entity);
        events.publish("market", "OrderSagaStateUpdated", saved.getId(), OrderSagaStateResponse.from(saved));
        return OrderSagaStateResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        OrderSagaState entity = require(id);
        repository.delete(entity);
        events.publish("market", "OrderSagaStateDeleted", id, null);
    }

    private OrderSagaState require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
