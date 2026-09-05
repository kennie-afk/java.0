package com.smartseason.marketplace.service;

import com.smartseason.marketplace.domain.MarketMatch;
import com.smartseason.marketplace.platform.EventPublisher;
import com.smartseason.marketplace.platform.PageResponse;
import com.smartseason.marketplace.platform.ResourceNotFoundException;
import com.smartseason.marketplace.platform.TenantContext;
import com.smartseason.marketplace.repo.MarketMatchRepository;
import com.smartseason.marketplace.web.dto.MarketMatchCreateRequest;
import com.smartseason.marketplace.web.dto.MarketMatchResponse;
import com.smartseason.marketplace.web.dto.MarketMatchUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MarketMatchService {

    private static final String RESOURCE = "MarketMatch";

    private final MarketMatchRepository repository;
    private final EventPublisher events;

    public MarketMatchService(MarketMatchRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<MarketMatchResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(MarketMatchResponse::from));
    }

    public MarketMatchResponse get(UUID id) {
        return MarketMatchResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public MarketMatchResponse create(MarketMatchCreateRequest request) {
        MarketMatch entity = new MarketMatch();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setListingId(request.listingId());
        entity.setDemandPostId(request.demandPostId());
        entity.setScore(request.score());
        entity.setMatchedAt(request.matchedAt());
        entity.setQuantity(request.quantity());
        entity.setOrderId(request.orderId());
        entity.setStatus(request.status());

        MarketMatch saved = repository.save(entity);
        events.publish("market", "MarketMatchCreated", saved.getId(), MarketMatchResponse.from(saved));
        return MarketMatchResponse.from(saved);
    }

    @Transactional
    public MarketMatchResponse update(UUID id, MarketMatchUpdateRequest request) {
        MarketMatch entity = require(id);
        if (request.listingId() != null) {
            entity.setListingId(request.listingId());
        }
        if (request.demandPostId() != null) {
            entity.setDemandPostId(request.demandPostId());
        }
        if (request.score() != null) {
            entity.setScore(request.score());
        }
        if (request.matchedAt() != null) {
            entity.setMatchedAt(request.matchedAt());
        }
        if (request.quantity() != null) {
            entity.setQuantity(request.quantity());
        }
        if (request.orderId() != null) {
            entity.setOrderId(request.orderId());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        MarketMatch saved = repository.save(entity);
        events.publish("market", "MarketMatchUpdated", saved.getId(), MarketMatchResponse.from(saved));
        return MarketMatchResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        MarketMatch entity = require(id);
        repository.delete(entity);
        events.publish("market", "MarketMatchDeleted", id, null);
    }

    private MarketMatch require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
