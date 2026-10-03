package com.smartseason.order.service;

import com.smartseason.order.domain.OrderSagaState;
import com.smartseason.order.platform.CountCache;
import com.smartseason.order.platform.CountCache;
import com.smartseason.order.platform.EventPublisher;
import com.smartseason.order.platform.ReferenceChecker;
import com.smartseason.order.platform.Cursor;
import com.smartseason.order.platform.CursorPage;
import com.smartseason.order.platform.PageResponse;
import com.smartseason.order.platform.ResourceNotFoundException;
import com.smartseason.order.platform.TenantContext;
import com.smartseason.order.repo.OrderSagaStateRepository;
import com.smartseason.order.web.dto.OrderSagaStateCreateRequest;
import com.smartseason.order.web.dto.OrderSagaStateResponse;
import com.smartseason.order.web.dto.OrderSagaStateUpdateRequest;
import com.smartseason.order.platform.ListFilter;
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
public class OrderSagaStateService {

    private static final String RESOURCE = "OrderSagaState";
    private static final String ENTITY = "order_saga_states";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("orderId", UUID.class),
            Map.entry("currentStep", String.class),
            Map.entry("stepStatus", OrderSagaState.StepStatus.class));

    private static final List<String> SEARCHABLE = List.of("currentStep");

    private final OrderSagaStateRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public OrderSagaStateService(OrderSagaStateRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<OrderSagaStateResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<OrderSagaState>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(OrderSagaStateResponse::from));
    }

    public PageResponse<OrderSagaStateResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(OrderSagaStateResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<OrderSagaStateResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<OrderSagaState> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(OrderSagaStateResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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

        references.require("PurchaseOrder", "orderId", request.orderId());
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
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "OrderSagaStateCreated", saved.getId(), OrderSagaStateResponse.from(saved));
        return OrderSagaStateResponse.from(saved);
    }

    @Transactional
    public OrderSagaStateResponse update(UUID id, OrderSagaStateUpdateRequest request) {
        OrderSagaState entity = require(id);
        references.require("PurchaseOrder", "orderId", request.orderId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "OrderSagaStateDeleted", id, null);
    }

    private OrderSagaState require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
