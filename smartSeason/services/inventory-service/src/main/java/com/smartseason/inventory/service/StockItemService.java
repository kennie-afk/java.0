package com.smartseason.inventory.service;

import com.smartseason.inventory.domain.StockItem;
import com.smartseason.inventory.platform.EventPublisher;
import com.smartseason.inventory.platform.PageResponse;
import com.smartseason.inventory.platform.ResourceNotFoundException;
import com.smartseason.inventory.platform.TenantContext;
import com.smartseason.inventory.repo.StockItemRepository;
import com.smartseason.inventory.web.dto.StockItemCreateRequest;
import com.smartseason.inventory.web.dto.StockItemResponse;
import com.smartseason.inventory.web.dto.StockItemUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StockItemService {

    private static final String RESOURCE = "StockItem";

    private final StockItemRepository repository;
    private final EventPublisher events;

    public StockItemService(StockItemRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<StockItemResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(StockItemResponse::from));
    }

    public StockItemResponse get(UUID id) {
        return StockItemResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public StockItemResponse create(StockItemCreateRequest request) {
        StockItem entity = new StockItem();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setWarehouseId(request.warehouseId());
        entity.setCommodityCode(request.commodityCode());
        entity.setGrade(request.grade());
        entity.setBatchId(request.batchId());
        entity.setQuantity(request.quantity());
        entity.setUnit(request.unit());
        entity.setReservedQuantity(request.reservedQuantity());
        entity.setExpiresOn(request.expiresOn());
        entity.setLastCountedAt(request.lastCountedAt());

        StockItem saved = repository.save(entity);
        events.publish("market", "StockItemCreated", saved.getId(), StockItemResponse.from(saved));
        return StockItemResponse.from(saved);
    }

    @Transactional
    public StockItemResponse update(UUID id, StockItemUpdateRequest request) {
        StockItem entity = require(id);
        if (request.warehouseId() != null) {
            entity.setWarehouseId(request.warehouseId());
        }
        if (request.commodityCode() != null) {
            entity.setCommodityCode(request.commodityCode());
        }
        if (request.grade() != null) {
            entity.setGrade(request.grade());
        }
        if (request.batchId() != null) {
            entity.setBatchId(request.batchId());
        }
        if (request.quantity() != null) {
            entity.setQuantity(request.quantity());
        }
        if (request.unit() != null) {
            entity.setUnit(request.unit());
        }
        if (request.reservedQuantity() != null) {
            entity.setReservedQuantity(request.reservedQuantity());
        }
        if (request.expiresOn() != null) {
            entity.setExpiresOn(request.expiresOn());
        }
        if (request.lastCountedAt() != null) {
            entity.setLastCountedAt(request.lastCountedAt());
        }

        StockItem saved = repository.save(entity);
        events.publish("market", "StockItemUpdated", saved.getId(), StockItemResponse.from(saved));
        return StockItemResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        StockItem entity = require(id);
        repository.delete(entity);
        events.publish("market", "StockItemDeleted", id, null);
    }

    private StockItem require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
