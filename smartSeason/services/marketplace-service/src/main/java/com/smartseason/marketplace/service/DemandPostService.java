package com.smartseason.marketplace.service;

import com.smartseason.marketplace.domain.DemandPost;
import com.smartseason.marketplace.platform.EventPublisher;
import com.smartseason.marketplace.platform.PageResponse;
import com.smartseason.marketplace.platform.ResourceNotFoundException;
import com.smartseason.marketplace.platform.TenantContext;
import com.smartseason.marketplace.repo.DemandPostRepository;
import com.smartseason.marketplace.web.dto.DemandPostCreateRequest;
import com.smartseason.marketplace.web.dto.DemandPostResponse;
import com.smartseason.marketplace.web.dto.DemandPostUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DemandPostService {

    private static final String RESOURCE = "DemandPost";

    private final DemandPostRepository repository;
    private final EventPublisher events;

    public DemandPostService(DemandPostRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<DemandPostResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(DemandPostResponse::from));
    }

    public DemandPostResponse get(UUID id) {
        return DemandPostResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public DemandPostResponse create(DemandPostCreateRequest request) {
        DemandPost entity = new DemandPost();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setBuyerOrgId(request.buyerOrgId());
        entity.setCommodityCode(request.commodityCode());
        entity.setGrade(request.grade());
        entity.setQuantity(request.quantity());
        entity.setUnit(request.unit());
        entity.setBidPrice(request.bidPrice());
        entity.setCurrency(request.currency());
        entity.setNeededBy(request.neededBy());
        entity.setDeliveryCounty(request.deliveryCounty());
        entity.setRecurring(request.recurring());
        entity.setStatus(request.status());
        entity.setNotes(request.notes());

        DemandPost saved = repository.save(entity);
        events.publish("market", "DemandPostCreated", saved.getId(), DemandPostResponse.from(saved));
        return DemandPostResponse.from(saved);
    }

    @Transactional
    public DemandPostResponse update(UUID id, DemandPostUpdateRequest request) {
        DemandPost entity = require(id);
        if (request.buyerOrgId() != null) {
            entity.setBuyerOrgId(request.buyerOrgId());
        }
        if (request.commodityCode() != null) {
            entity.setCommodityCode(request.commodityCode());
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
        if (request.bidPrice() != null) {
            entity.setBidPrice(request.bidPrice());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.neededBy() != null) {
            entity.setNeededBy(request.neededBy());
        }
        if (request.deliveryCounty() != null) {
            entity.setDeliveryCounty(request.deliveryCounty());
        }
        if (request.recurring() != null) {
            entity.setRecurring(request.recurring());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }

        DemandPost saved = repository.save(entity);
        events.publish("market", "DemandPostUpdated", saved.getId(), DemandPostResponse.from(saved));
        return DemandPostResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        DemandPost entity = require(id);
        repository.delete(entity);
        events.publish("market", "DemandPostDeleted", id, null);
    }

    private DemandPost require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
