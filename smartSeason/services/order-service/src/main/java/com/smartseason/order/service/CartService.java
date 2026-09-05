package com.smartseason.order.service;

import com.smartseason.order.domain.Cart;
import com.smartseason.order.platform.EventPublisher;
import com.smartseason.order.platform.PageResponse;
import com.smartseason.order.platform.ResourceNotFoundException;
import com.smartseason.order.platform.TenantContext;
import com.smartseason.order.repo.CartRepository;
import com.smartseason.order.web.dto.CartCreateRequest;
import com.smartseason.order.web.dto.CartResponse;
import com.smartseason.order.web.dto.CartUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CartService {

    private static final String RESOURCE = "Cart";

    private final CartRepository repository;
    private final EventPublisher events;

    public CartService(CartRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<CartResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(CartResponse::from));
    }

    public CartResponse get(UUID id) {
        return CartResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public CartResponse create(CartCreateRequest request) {
        Cart entity = new Cart();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setBuyerOrgId(request.buyerOrgId());
        entity.setBuyerUserId(request.buyerUserId());
        entity.setCurrency(request.currency());
        entity.setStatus(request.status());
        entity.setExpiresAt(request.expiresAt());

        Cart saved = repository.save(entity);
        events.publish("market", "CartCreated", saved.getId(), CartResponse.from(saved));
        return CartResponse.from(saved);
    }

    @Transactional
    public CartResponse update(UUID id, CartUpdateRequest request) {
        Cart entity = require(id);
        if (request.buyerOrgId() != null) {
            entity.setBuyerOrgId(request.buyerOrgId());
        }
        if (request.buyerUserId() != null) {
            entity.setBuyerUserId(request.buyerUserId());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.expiresAt() != null) {
            entity.setExpiresAt(request.expiresAt());
        }

        Cart saved = repository.save(entity);
        events.publish("market", "CartUpdated", saved.getId(), CartResponse.from(saved));
        return CartResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Cart entity = require(id);
        repository.delete(entity);
        events.publish("market", "CartDeleted", id, null);
    }

    private Cart require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
