package com.smartseason.pricing.service;

import com.smartseason.pricing.domain.MarketIndex;
import com.smartseason.pricing.platform.CountCache;
import com.smartseason.pricing.platform.CountCache;
import com.smartseason.pricing.platform.EventPublisher;
import com.smartseason.pricing.platform.PageResponse;
import com.smartseason.pricing.platform.ResourceNotFoundException;
import com.smartseason.pricing.platform.TenantContext;
import com.smartseason.pricing.repo.MarketIndexRepository;
import com.smartseason.pricing.web.dto.MarketIndexCreateRequest;
import com.smartseason.pricing.web.dto.MarketIndexResponse;
import com.smartseason.pricing.web.dto.MarketIndexUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MarketIndexService {

    private static final String RESOURCE = "MarketIndex";
    private static final String ENTITY = "market_indices";

    private final MarketIndexRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public MarketIndexService(MarketIndexRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<MarketIndexResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(MarketIndexResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public MarketIndexResponse get(UUID id) {
        return MarketIndexResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public MarketIndexResponse create(MarketIndexCreateRequest request) {
        MarketIndex entity = new MarketIndex();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCommodityCode(request.commodityCode());
        entity.setRegion(request.region());
        entity.setPeriodStart(request.periodStart());
        entity.setPeriodEnd(request.periodEnd());
        entity.setIndexValue(request.indexValue());
        entity.setChangePct(request.changePct());
        entity.setBasis(request.basis());
        entity.setComputedAt(request.computedAt());

        MarketIndex saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "MarketIndexCreated", saved.getId(), MarketIndexResponse.from(saved));
        return MarketIndexResponse.from(saved);
    }

    @Transactional
    public MarketIndexResponse update(UUID id, MarketIndexUpdateRequest request) {
        MarketIndex entity = require(id);
        if (request.commodityCode() != null) {
            entity.setCommodityCode(request.commodityCode());
        }
        if (request.region() != null) {
            entity.setRegion(request.region());
        }
        if (request.periodStart() != null) {
            entity.setPeriodStart(request.periodStart());
        }
        if (request.periodEnd() != null) {
            entity.setPeriodEnd(request.periodEnd());
        }
        if (request.indexValue() != null) {
            entity.setIndexValue(request.indexValue());
        }
        if (request.changePct() != null) {
            entity.setChangePct(request.changePct());
        }
        if (request.basis() != null) {
            entity.setBasis(request.basis());
        }
        if (request.computedAt() != null) {
            entity.setComputedAt(request.computedAt());
        }

        MarketIndex saved = repository.save(entity);
        events.publish("market", "MarketIndexUpdated", saved.getId(), MarketIndexResponse.from(saved));
        return MarketIndexResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        MarketIndex entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "MarketIndexDeleted", id, null);
    }

    private MarketIndex require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
