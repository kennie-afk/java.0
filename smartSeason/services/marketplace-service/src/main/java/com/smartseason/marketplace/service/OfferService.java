package com.smartseason.marketplace.service;

import com.smartseason.marketplace.domain.Offer;
import com.smartseason.marketplace.platform.EventPublisher;
import com.smartseason.marketplace.platform.PageResponse;
import com.smartseason.marketplace.platform.ResourceNotFoundException;
import com.smartseason.marketplace.platform.TenantContext;
import com.smartseason.marketplace.repo.OfferRepository;
import com.smartseason.marketplace.web.dto.OfferCreateRequest;
import com.smartseason.marketplace.web.dto.OfferResponse;
import com.smartseason.marketplace.web.dto.OfferUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OfferService {

    private static final String RESOURCE = "Offer";

    private final OfferRepository repository;
    private final EventPublisher events;

    public OfferService(OfferRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<OfferResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(OfferResponse::from));
    }

    public OfferResponse get(UUID id) {
        return OfferResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public OfferResponse create(OfferCreateRequest request) {
        Offer entity = new Offer();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setListingId(request.listingId());
        entity.setDemandPostId(request.demandPostId());
        entity.setFromOrgId(request.fromOrgId());
        entity.setToOrgId(request.toOrgId());
        entity.setQuantity(request.quantity());
        entity.setUnitPrice(request.unitPrice());
        entity.setCurrency(request.currency());
        entity.setExpiresAt(request.expiresAt());
        entity.setStatus(request.status());
        entity.setCounterOfferId(request.counterOfferId());
        entity.setMessage(request.message());
        entity.setRespondedAt(request.respondedAt());

        Offer saved = repository.save(entity);
        events.publish("market", "OfferCreated", saved.getId(), OfferResponse.from(saved));
        return OfferResponse.from(saved);
    }

    @Transactional
    public OfferResponse update(UUID id, OfferUpdateRequest request) {
        Offer entity = require(id);
        if (request.listingId() != null) {
            entity.setListingId(request.listingId());
        }
        if (request.demandPostId() != null) {
            entity.setDemandPostId(request.demandPostId());
        }
        if (request.fromOrgId() != null) {
            entity.setFromOrgId(request.fromOrgId());
        }
        if (request.toOrgId() != null) {
            entity.setToOrgId(request.toOrgId());
        }
        if (request.quantity() != null) {
            entity.setQuantity(request.quantity());
        }
        if (request.unitPrice() != null) {
            entity.setUnitPrice(request.unitPrice());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.expiresAt() != null) {
            entity.setExpiresAt(request.expiresAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.counterOfferId() != null) {
            entity.setCounterOfferId(request.counterOfferId());
        }
        if (request.message() != null) {
            entity.setMessage(request.message());
        }
        if (request.respondedAt() != null) {
            entity.setRespondedAt(request.respondedAt());
        }

        Offer saved = repository.save(entity);
        events.publish("market", "OfferUpdated", saved.getId(), OfferResponse.from(saved));
        return OfferResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Offer entity = require(id);
        repository.delete(entity);
        events.publish("market", "OfferDeleted", id, null);
    }

    private Offer require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
