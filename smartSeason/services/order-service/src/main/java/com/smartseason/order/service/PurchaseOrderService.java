package com.smartseason.order.service;

import com.smartseason.order.domain.PurchaseOrder;
import com.smartseason.order.platform.CountCache;
import com.smartseason.order.platform.CountCache;
import com.smartseason.order.platform.EventPublisher;
import com.smartseason.order.platform.PageResponse;
import com.smartseason.order.platform.ResourceNotFoundException;
import com.smartseason.order.platform.TenantContext;
import com.smartseason.order.repo.PurchaseOrderRepository;
import com.smartseason.order.web.dto.PurchaseOrderCreateRequest;
import com.smartseason.order.web.dto.PurchaseOrderResponse;
import com.smartseason.order.web.dto.PurchaseOrderUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PurchaseOrderService {

    private static final String RESOURCE = "PurchaseOrder";
    private static final String ENTITY = "orders";

    private final PurchaseOrderRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public PurchaseOrderService(PurchaseOrderRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<PurchaseOrderResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(PurchaseOrderResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public PurchaseOrderResponse get(UUID id) {
        return PurchaseOrderResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderCreateRequest request) {
        PurchaseOrder entity = new PurchaseOrder();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setOrderNumber(request.orderNumber());
        entity.setBuyerOrgId(request.buyerOrgId());
        entity.setSellerOrgId(request.sellerOrgId());
        entity.setCurrency(request.currency());
        entity.setSubtotal(request.subtotal());
        entity.setDeliveryFee(request.deliveryFee());
        entity.setPlatformFee(request.platformFee());
        entity.setTotalAmount(request.totalAmount());
        entity.setPlacedAt(request.placedAt());
        entity.setConfirmedAt(request.confirmedAt());
        entity.setFulfilledAt(request.fulfilledAt());
        entity.setCancelledAt(request.cancelledAt());
        entity.setCancellationReason(request.cancellationReason());
        entity.setDeliveryCounty(request.deliveryCounty());
        entity.setDeliveryAddress(request.deliveryAddress());
        entity.setDeliveryLat(request.deliveryLat());
        entity.setDeliveryLng(request.deliveryLng());
        entity.setPaymentIntentId(request.paymentIntentId());
        entity.setTransportJobId(request.transportJobId());
        entity.setStatus(request.status());
        entity.setIdempotencyKey(request.idempotencyKey());

        PurchaseOrder saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "PurchaseOrderCreated", saved.getId(), PurchaseOrderResponse.from(saved));
        return PurchaseOrderResponse.from(saved);
    }

    @Transactional
    public PurchaseOrderResponse update(UUID id, PurchaseOrderUpdateRequest request) {
        PurchaseOrder entity = require(id);
        if (request.orderNumber() != null) {
            entity.setOrderNumber(request.orderNumber());
        }
        if (request.buyerOrgId() != null) {
            entity.setBuyerOrgId(request.buyerOrgId());
        }
        if (request.sellerOrgId() != null) {
            entity.setSellerOrgId(request.sellerOrgId());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.subtotal() != null) {
            entity.setSubtotal(request.subtotal());
        }
        if (request.deliveryFee() != null) {
            entity.setDeliveryFee(request.deliveryFee());
        }
        if (request.platformFee() != null) {
            entity.setPlatformFee(request.platformFee());
        }
        if (request.totalAmount() != null) {
            entity.setTotalAmount(request.totalAmount());
        }
        if (request.placedAt() != null) {
            entity.setPlacedAt(request.placedAt());
        }
        if (request.confirmedAt() != null) {
            entity.setConfirmedAt(request.confirmedAt());
        }
        if (request.fulfilledAt() != null) {
            entity.setFulfilledAt(request.fulfilledAt());
        }
        if (request.cancelledAt() != null) {
            entity.setCancelledAt(request.cancelledAt());
        }
        if (request.cancellationReason() != null) {
            entity.setCancellationReason(request.cancellationReason());
        }
        if (request.deliveryCounty() != null) {
            entity.setDeliveryCounty(request.deliveryCounty());
        }
        if (request.deliveryAddress() != null) {
            entity.setDeliveryAddress(request.deliveryAddress());
        }
        if (request.deliveryLat() != null) {
            entity.setDeliveryLat(request.deliveryLat());
        }
        if (request.deliveryLng() != null) {
            entity.setDeliveryLng(request.deliveryLng());
        }
        if (request.paymentIntentId() != null) {
            entity.setPaymentIntentId(request.paymentIntentId());
        }
        if (request.transportJobId() != null) {
            entity.setTransportJobId(request.transportJobId());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.idempotencyKey() != null) {
            entity.setIdempotencyKey(request.idempotencyKey());
        }

        PurchaseOrder saved = repository.save(entity);
        events.publish("market", "PurchaseOrderUpdated", saved.getId(), PurchaseOrderResponse.from(saved));
        return PurchaseOrderResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        PurchaseOrder entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "PurchaseOrderDeleted", id, null);
    }

    private PurchaseOrder require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
