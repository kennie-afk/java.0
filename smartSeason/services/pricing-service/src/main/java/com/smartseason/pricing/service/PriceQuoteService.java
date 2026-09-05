package com.smartseason.pricing.service;

import com.smartseason.pricing.domain.PriceQuote;
import com.smartseason.pricing.platform.EventPublisher;
import com.smartseason.pricing.platform.PageResponse;
import com.smartseason.pricing.platform.ResourceNotFoundException;
import com.smartseason.pricing.platform.TenantContext;
import com.smartseason.pricing.repo.PriceQuoteRepository;
import com.smartseason.pricing.web.dto.PriceQuoteCreateRequest;
import com.smartseason.pricing.web.dto.PriceQuoteResponse;
import com.smartseason.pricing.web.dto.PriceQuoteUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PriceQuoteService {

    private static final String RESOURCE = "PriceQuote";

    private final PriceQuoteRepository repository;
    private final EventPublisher events;

    public PriceQuoteService(PriceQuoteRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<PriceQuoteResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(PriceQuoteResponse::from));
    }

    public PriceQuoteResponse get(UUID id) {
        return PriceQuoteResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public PriceQuoteResponse create(PriceQuoteCreateRequest request) {
        PriceQuote entity = new PriceQuote();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCommodityCode(request.commodityCode());
        entity.setGrade(request.grade());
        entity.setCounty(request.county());
        entity.setQuantity(request.quantity());
        entity.setSuggestedPrice(request.suggestedPrice());
        entity.setConfidence(request.confidence());
        entity.setCurrency(request.currency());
        entity.setValidUntil(request.validUntil());
        entity.setRationale(request.rationale());
        entity.setRequestedBy(request.requestedBy());

        PriceQuote saved = repository.save(entity);
        events.publish("market", "PriceQuoteCreated", saved.getId(), PriceQuoteResponse.from(saved));
        return PriceQuoteResponse.from(saved);
    }

    @Transactional
    public PriceQuoteResponse update(UUID id, PriceQuoteUpdateRequest request) {
        PriceQuote entity = require(id);
        if (request.commodityCode() != null) {
            entity.setCommodityCode(request.commodityCode());
        }
        if (request.grade() != null) {
            entity.setGrade(request.grade());
        }
        if (request.county() != null) {
            entity.setCounty(request.county());
        }
        if (request.quantity() != null) {
            entity.setQuantity(request.quantity());
        }
        if (request.suggestedPrice() != null) {
            entity.setSuggestedPrice(request.suggestedPrice());
        }
        if (request.confidence() != null) {
            entity.setConfidence(request.confidence());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.validUntil() != null) {
            entity.setValidUntil(request.validUntil());
        }
        if (request.rationale() != null) {
            entity.setRationale(request.rationale());
        }
        if (request.requestedBy() != null) {
            entity.setRequestedBy(request.requestedBy());
        }

        PriceQuote saved = repository.save(entity);
        events.publish("market", "PriceQuoteUpdated", saved.getId(), PriceQuoteResponse.from(saved));
        return PriceQuoteResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        PriceQuote entity = require(id);
        repository.delete(entity);
        events.publish("market", "PriceQuoteDeleted", id, null);
    }

    private PriceQuote require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
