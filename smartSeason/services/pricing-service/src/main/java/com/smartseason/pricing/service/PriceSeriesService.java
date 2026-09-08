package com.smartseason.pricing.service;

import com.smartseason.pricing.domain.PriceSeries;
import com.smartseason.pricing.platform.CountCache;
import com.smartseason.pricing.platform.CountCache;
import com.smartseason.pricing.platform.EventPublisher;
import com.smartseason.pricing.platform.PageResponse;
import com.smartseason.pricing.platform.ResourceNotFoundException;
import com.smartseason.pricing.platform.TenantContext;
import com.smartseason.pricing.repo.PriceSeriesRepository;
import com.smartseason.pricing.web.dto.PriceSeriesCreateRequest;
import com.smartseason.pricing.web.dto.PriceSeriesResponse;
import com.smartseason.pricing.web.dto.PriceSeriesUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PriceSeriesService {

    private static final String RESOURCE = "PriceSeries";
    private static final String ENTITY = "price_series";

    private final PriceSeriesRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public PriceSeriesService(PriceSeriesRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<PriceSeriesResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(PriceSeriesResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public PriceSeriesResponse get(UUID id) {
        return PriceSeriesResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public PriceSeriesResponse create(PriceSeriesCreateRequest request) {
        PriceSeries entity = new PriceSeries();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCommodityCode(request.commodityCode());
        entity.setCounty(request.county());
        entity.setMarketName(request.marketName());
        entity.setGrade(request.grade());
        entity.setObservedOn(request.observedOn());
        entity.setUnit(request.unit());
        entity.setMinPrice(request.minPrice());
        entity.setMaxPrice(request.maxPrice());
        entity.setAvgPrice(request.avgPrice());
        entity.setCurrency(request.currency());
        entity.setSource(request.source());
        entity.setVolumeKg(request.volumeKg());

        PriceSeries saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "PriceSeriesCreated", saved.getId(), PriceSeriesResponse.from(saved));
        return PriceSeriesResponse.from(saved);
    }

    @Transactional
    public PriceSeriesResponse update(UUID id, PriceSeriesUpdateRequest request) {
        PriceSeries entity = require(id);
        if (request.commodityCode() != null) {
            entity.setCommodityCode(request.commodityCode());
        }
        if (request.county() != null) {
            entity.setCounty(request.county());
        }
        if (request.marketName() != null) {
            entity.setMarketName(request.marketName());
        }
        if (request.grade() != null) {
            entity.setGrade(request.grade());
        }
        if (request.observedOn() != null) {
            entity.setObservedOn(request.observedOn());
        }
        if (request.unit() != null) {
            entity.setUnit(request.unit());
        }
        if (request.minPrice() != null) {
            entity.setMinPrice(request.minPrice());
        }
        if (request.maxPrice() != null) {
            entity.setMaxPrice(request.maxPrice());
        }
        if (request.avgPrice() != null) {
            entity.setAvgPrice(request.avgPrice());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.source() != null) {
            entity.setSource(request.source());
        }
        if (request.volumeKg() != null) {
            entity.setVolumeKg(request.volumeKg());
        }

        PriceSeries saved = repository.save(entity);
        events.publish("market", "PriceSeriesUpdated", saved.getId(), PriceSeriesResponse.from(saved));
        return PriceSeriesResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        PriceSeries entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "PriceSeriesDeleted", id, null);
    }

    private PriceSeries require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
