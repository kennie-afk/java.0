package com.smartseason.catalog.service;

import com.smartseason.catalog.domain.Commodity;
import com.smartseason.catalog.platform.CountCache;
import com.smartseason.catalog.platform.CountCache;
import com.smartseason.catalog.platform.EventPublisher;
import com.smartseason.catalog.platform.PageResponse;
import com.smartseason.catalog.platform.ResourceNotFoundException;
import com.smartseason.catalog.platform.TenantContext;
import com.smartseason.catalog.repo.CommodityRepository;
import com.smartseason.catalog.web.dto.CommodityCreateRequest;
import com.smartseason.catalog.web.dto.CommodityResponse;
import com.smartseason.catalog.web.dto.CommodityUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommodityService {

    private static final String RESOURCE = "Commodity";
    private static final String ENTITY = "commodities";

    private final CommodityRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public CommodityService(CommodityRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<CommodityResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(CommodityResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CommodityResponse get(UUID id) {
        return CommodityResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public CommodityResponse create(CommodityCreateRequest request) {
        Commodity entity = new Commodity();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setCategory(request.category());
        entity.setDefaultUnit(request.defaultUnit());
        entity.setPerishable(request.perishable());
        entity.setShelfLifeDays(request.shelfLifeDays());
        entity.setImageUrl(request.imageUrl());

        Commodity saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "CommodityCreated", saved.getId(), CommodityResponse.from(saved));
        return CommodityResponse.from(saved);
    }

    @Transactional
    public CommodityResponse update(UUID id, CommodityUpdateRequest request) {
        Commodity entity = require(id);
        if (request.code() != null) {
            entity.setCode(request.code());
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.category() != null) {
            entity.setCategory(request.category());
        }
        if (request.defaultUnit() != null) {
            entity.setDefaultUnit(request.defaultUnit());
        }
        if (request.perishable() != null) {
            entity.setPerishable(request.perishable());
        }
        if (request.shelfLifeDays() != null) {
            entity.setShelfLifeDays(request.shelfLifeDays());
        }
        if (request.imageUrl() != null) {
            entity.setImageUrl(request.imageUrl());
        }

        Commodity saved = repository.save(entity);
        events.publish("market", "CommodityUpdated", saved.getId(), CommodityResponse.from(saved));
        return CommodityResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Commodity entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "CommodityDeleted", id, null);
    }

    private Commodity require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
