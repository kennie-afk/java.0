package com.smartseason.marketplace.service;

import com.smartseason.marketplace.domain.SupplyListing;
import com.smartseason.marketplace.platform.EventPublisher;
import com.smartseason.marketplace.platform.PageResponse;
import com.smartseason.marketplace.platform.ResourceNotFoundException;
import com.smartseason.marketplace.platform.TenantContext;
import com.smartseason.marketplace.repo.SupplyListingRepository;
import com.smartseason.marketplace.web.dto.SupplyListingCreateRequest;
import com.smartseason.marketplace.web.dto.SupplyListingResponse;
import com.smartseason.marketplace.web.dto.SupplyListingUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SupplyListingService {

    private static final String RESOURCE = "SupplyListing";

    private final SupplyListingRepository repository;
    private final EventPublisher events;

    public SupplyListingService(SupplyListingRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<SupplyListingResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(SupplyListingResponse::from));
    }

    public SupplyListingResponse get(UUID id) {
        return SupplyListingResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public SupplyListingResponse create(SupplyListingCreateRequest request) {
        SupplyListing entity = new SupplyListing();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setSellerOrgId(request.sellerOrgId());
        entity.setFarmId(request.farmId());
        entity.setCommodityCode(request.commodityCode());
        entity.setVariety(request.variety());
        entity.setGrade(request.grade());
        entity.setQuantity(request.quantity());
        entity.setUnit(request.unit());
        entity.setAskPrice(request.askPrice());
        entity.setCurrency(request.currency());
        entity.setAvailableFrom(request.availableFrom());
        entity.setAvailableTo(request.availableTo());
        entity.setCounty(request.county());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setBatchId(request.batchId());
        entity.setPhotoUrls(request.photoUrls());
        entity.setDescription(request.description());
        entity.setStatus(request.status());

        SupplyListing saved = repository.save(entity);
        events.publish("market", "SupplyListingCreated", saved.getId(), SupplyListingResponse.from(saved));
        return SupplyListingResponse.from(saved);
    }

    @Transactional
    public SupplyListingResponse update(UUID id, SupplyListingUpdateRequest request) {
        SupplyListing entity = require(id);
        if (request.sellerOrgId() != null) {
            entity.setSellerOrgId(request.sellerOrgId());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.commodityCode() != null) {
            entity.setCommodityCode(request.commodityCode());
        }
        if (request.variety() != null) {
            entity.setVariety(request.variety());
        }
        if (request.grade() != null) {
            entity.setGrade(request.grade());
        }
        if (request.quantity() != null) {
            entity.setQuantity(request.quantity());
        }
        if (request.unit() != null) {
            entity.setUnit(request.unit());
        }
        if (request.askPrice() != null) {
            entity.setAskPrice(request.askPrice());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.availableFrom() != null) {
            entity.setAvailableFrom(request.availableFrom());
        }
        if (request.availableTo() != null) {
            entity.setAvailableTo(request.availableTo());
        }
        if (request.county() != null) {
            entity.setCounty(request.county());
        }
        if (request.latitude() != null) {
            entity.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            entity.setLongitude(request.longitude());
        }
        if (request.batchId() != null) {
            entity.setBatchId(request.batchId());
        }
        if (request.photoUrls() != null) {
            entity.setPhotoUrls(request.photoUrls());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        SupplyListing saved = repository.save(entity);
        events.publish("market", "SupplyListingUpdated", saved.getId(), SupplyListingResponse.from(saved));
        return SupplyListingResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        SupplyListing entity = require(id);
        repository.delete(entity);
        events.publish("market", "SupplyListingDeleted", id, null);
    }

    private SupplyListing require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
