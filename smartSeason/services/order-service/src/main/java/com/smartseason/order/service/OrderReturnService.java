package com.smartseason.order.service;

import com.smartseason.order.domain.OrderReturn;
import com.smartseason.order.platform.EventPublisher;
import com.smartseason.order.platform.PageResponse;
import com.smartseason.order.platform.ResourceNotFoundException;
import com.smartseason.order.platform.TenantContext;
import com.smartseason.order.repo.OrderReturnRepository;
import com.smartseason.order.web.dto.OrderReturnCreateRequest;
import com.smartseason.order.web.dto.OrderReturnResponse;
import com.smartseason.order.web.dto.OrderReturnUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderReturnService {

    private static final String RESOURCE = "OrderReturn";

    private final OrderReturnRepository repository;
    private final EventPublisher events;

    public OrderReturnService(OrderReturnRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<OrderReturnResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(OrderReturnResponse::from));
    }

    public OrderReturnResponse get(UUID id) {
        return OrderReturnResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public OrderReturnResponse create(OrderReturnCreateRequest request) {
        OrderReturn entity = new OrderReturn();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setOrderId(request.orderId());
        entity.setOrderLineId(request.orderLineId());
        entity.setQuantity(request.quantity());
        entity.setReason(request.reason());
        entity.setRequestedBy(request.requestedBy());
        entity.setRequestedAt(request.requestedAt());
        entity.setRefundAmount(request.refundAmount());
        entity.setStatus(request.status());

        OrderReturn saved = repository.save(entity);
        events.publish("market", "OrderReturnCreated", saved.getId(), OrderReturnResponse.from(saved));
        return OrderReturnResponse.from(saved);
    }

    @Transactional
    public OrderReturnResponse update(UUID id, OrderReturnUpdateRequest request) {
        OrderReturn entity = require(id);
        if (request.orderId() != null) {
            entity.setOrderId(request.orderId());
        }
        if (request.orderLineId() != null) {
            entity.setOrderLineId(request.orderLineId());
        }
        if (request.quantity() != null) {
            entity.setQuantity(request.quantity());
        }
        if (request.reason() != null) {
            entity.setReason(request.reason());
        }
        if (request.requestedBy() != null) {
            entity.setRequestedBy(request.requestedBy());
        }
        if (request.requestedAt() != null) {
            entity.setRequestedAt(request.requestedAt());
        }
        if (request.refundAmount() != null) {
            entity.setRefundAmount(request.refundAmount());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        OrderReturn saved = repository.save(entity);
        events.publish("market", "OrderReturnUpdated", saved.getId(), OrderReturnResponse.from(saved));
        return OrderReturnResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        OrderReturn entity = require(id);
        repository.delete(entity);
        events.publish("market", "OrderReturnDeleted", id, null);
    }

    private OrderReturn require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
