package com.smartseason.order.service;

import com.smartseason.order.domain.OrderLine;
import com.smartseason.order.platform.EventPublisher;
import com.smartseason.order.platform.PageResponse;
import com.smartseason.order.platform.ResourceNotFoundException;
import com.smartseason.order.platform.TenantContext;
import com.smartseason.order.repo.OrderLineRepository;
import com.smartseason.order.web.dto.OrderLineCreateRequest;
import com.smartseason.order.web.dto.OrderLineResponse;
import com.smartseason.order.web.dto.OrderLineUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderLineService {

    private static final String RESOURCE = "OrderLine";

    private final OrderLineRepository repository;
    private final EventPublisher events;

    public OrderLineService(OrderLineRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<OrderLineResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(OrderLineResponse::from));
    }

    public OrderLineResponse get(UUID id) {
        return OrderLineResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public OrderLineResponse create(OrderLineCreateRequest request) {
        OrderLine entity = new OrderLine();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setOrderId(request.orderId());
        entity.setListingId(request.listingId());
        entity.setCommodityCode(request.commodityCode());
        entity.setGrade(request.grade());
        entity.setQuantity(request.quantity());
        entity.setUnit(request.unit());
        entity.setUnitPrice(request.unitPrice());
        entity.setLineTotal(request.lineTotal());
        entity.setBatchId(request.batchId());
        entity.setFulfilledQuantity(request.fulfilledQuantity());

        OrderLine saved = repository.save(entity);
        events.publish("market", "OrderLineCreated", saved.getId(), OrderLineResponse.from(saved));
        return OrderLineResponse.from(saved);
    }

    @Transactional
    public OrderLineResponse update(UUID id, OrderLineUpdateRequest request) {
        OrderLine entity = require(id);
        if (request.orderId() != null) {
            entity.setOrderId(request.orderId());
        }
        if (request.listingId() != null) {
            entity.setListingId(request.listingId());
        }
        if (request.commodityCode() != null) {
            entity.setCommodityCode(request.commodityCode());
        }
        if (request.grade() != null) {
            entity.setGrade(request.grade());
        }
        if (request.quantity() != null) {
            entity.setQuantity(request.quantity());
        }
        if (request.unit() != null) {
            entity.setUnit(request.unit());
        }
        if (request.unitPrice() != null) {
            entity.setUnitPrice(request.unitPrice());
        }
        if (request.lineTotal() != null) {
            entity.setLineTotal(request.lineTotal());
        }
        if (request.batchId() != null) {
            entity.setBatchId(request.batchId());
        }
        if (request.fulfilledQuantity() != null) {
            entity.setFulfilledQuantity(request.fulfilledQuantity());
        }

        OrderLine saved = repository.save(entity);
        events.publish("market", "OrderLineUpdated", saved.getId(), OrderLineResponse.from(saved));
        return OrderLineResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        OrderLine entity = require(id);
        repository.delete(entity);
        events.publish("market", "OrderLineDeleted", id, null);
    }

    private OrderLine require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
