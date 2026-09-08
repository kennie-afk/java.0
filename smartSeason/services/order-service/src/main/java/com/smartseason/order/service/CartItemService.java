package com.smartseason.order.service;

import com.smartseason.order.domain.CartItem;
import com.smartseason.order.platform.CountCache;
import com.smartseason.order.platform.CountCache;
import com.smartseason.order.platform.EventPublisher;
import com.smartseason.order.platform.PageResponse;
import com.smartseason.order.platform.ResourceNotFoundException;
import com.smartseason.order.platform.TenantContext;
import com.smartseason.order.repo.CartItemRepository;
import com.smartseason.order.web.dto.CartItemCreateRequest;
import com.smartseason.order.web.dto.CartItemResponse;
import com.smartseason.order.web.dto.CartItemUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CartItemService {

    private static final String RESOURCE = "CartItem";
    private static final String ENTITY = "cart_items";

    private final CartItemRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public CartItemService(CartItemRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<CartItemResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(CartItemResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CartItemResponse get(UUID id) {
        return CartItemResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public CartItemResponse create(CartItemCreateRequest request) {
        CartItem entity = new CartItem();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCartId(request.cartId());
        entity.setListingId(request.listingId());
        entity.setCommodityCode(request.commodityCode());
        entity.setQuantity(request.quantity());
        entity.setUnit(request.unit());
        entity.setUnitPrice(request.unitPrice());
        entity.setSellerOrgId(request.sellerOrgId());

        CartItem saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "CartItemCreated", saved.getId(), CartItemResponse.from(saved));
        return CartItemResponse.from(saved);
    }

    @Transactional
    public CartItemResponse update(UUID id, CartItemUpdateRequest request) {
        CartItem entity = require(id);
        if (request.cartId() != null) {
            entity.setCartId(request.cartId());
        }
        if (request.listingId() != null) {
            entity.setListingId(request.listingId());
        }
        if (request.commodityCode() != null) {
            entity.setCommodityCode(request.commodityCode());
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
        if (request.sellerOrgId() != null) {
            entity.setSellerOrgId(request.sellerOrgId());
        }

        CartItem saved = repository.save(entity);
        events.publish("market", "CartItemUpdated", saved.getId(), CartItemResponse.from(saved));
        return CartItemResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        CartItem entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "CartItemDeleted", id, null);
    }

    private CartItem require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
