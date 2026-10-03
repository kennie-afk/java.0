package com.smartseason.order.service;

import com.smartseason.order.domain.CartItem;
import com.smartseason.order.platform.CountCache;
import com.smartseason.order.platform.CountCache;
import com.smartseason.order.platform.EventPublisher;
import com.smartseason.order.platform.Cursor;
import com.smartseason.order.platform.CursorPage;
import com.smartseason.order.platform.PageResponse;
import com.smartseason.order.platform.ResourceNotFoundException;
import com.smartseason.order.platform.TenantContext;
import com.smartseason.order.repo.CartItemRepository;
import com.smartseason.order.web.dto.CartItemCreateRequest;
import com.smartseason.order.web.dto.CartItemResponse;
import com.smartseason.order.web.dto.CartItemUpdateRequest;
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
public class CartItemService {

    private static final String RESOURCE = "CartItem";
    private static final String ENTITY = "cart_items";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("cartId", UUID.class),
            Map.entry("listingId", UUID.class),
            Map.entry("commodityCode", String.class),
            Map.entry("unit", String.class),
            Map.entry("sellerOrgId", UUID.class));

    private static final List<String> SEARCHABLE = List.of("commodityCode", "unit");

    private final CartItemRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public CartItemService(CartItemRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<CartItemResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<CartItem>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(CartItemResponse::from));
    }

    public PageResponse<CartItemResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(CartItemResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<CartItemResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<CartItem> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(CartItemResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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
