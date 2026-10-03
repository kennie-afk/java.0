package com.smartseason.order.service;

import com.smartseason.order.domain.OrderReturn;
import com.smartseason.order.platform.CountCache;
import com.smartseason.order.platform.CountCache;
import com.smartseason.order.platform.EventPublisher;
import com.smartseason.order.platform.Cursor;
import com.smartseason.order.platform.CursorPage;
import com.smartseason.order.platform.PageResponse;
import com.smartseason.order.platform.ResourceNotFoundException;
import com.smartseason.order.platform.TenantContext;
import com.smartseason.order.repo.OrderReturnRepository;
import com.smartseason.order.web.dto.OrderReturnCreateRequest;
import com.smartseason.order.web.dto.OrderReturnResponse;
import com.smartseason.order.web.dto.OrderReturnUpdateRequest;
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
public class OrderReturnService {

    private static final String RESOURCE = "OrderReturn";
    private static final String ENTITY = "order_returns";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("orderId", UUID.class),
            Map.entry("orderLineId", UUID.class),
            Map.entry("requestedBy", UUID.class),
            Map.entry("status", OrderReturn.Status.class));

    private static final List<String> SEARCHABLE = List.of();

    private final OrderReturnRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public OrderReturnService(OrderReturnRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<OrderReturnResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<OrderReturn>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(OrderReturnResponse::from));
    }

    public PageResponse<OrderReturnResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(OrderReturnResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<OrderReturnResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<OrderReturn> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(OrderReturnResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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
        counts.invalidate(ENTITY, saved.getTenantId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "OrderReturnDeleted", id, null);
    }

    private OrderReturn require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
