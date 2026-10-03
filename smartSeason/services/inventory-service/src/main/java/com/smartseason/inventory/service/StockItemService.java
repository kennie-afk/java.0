package com.smartseason.inventory.service;

import com.smartseason.inventory.domain.StockItem;
import com.smartseason.inventory.platform.CountCache;
import com.smartseason.inventory.platform.CountCache;
import com.smartseason.inventory.platform.EventPublisher;
import com.smartseason.inventory.platform.Cursor;
import com.smartseason.inventory.platform.CursorPage;
import com.smartseason.inventory.platform.PageResponse;
import com.smartseason.inventory.platform.ResourceNotFoundException;
import com.smartseason.inventory.platform.TenantContext;
import com.smartseason.inventory.repo.StockItemRepository;
import com.smartseason.inventory.web.dto.StockItemCreateRequest;
import com.smartseason.inventory.web.dto.StockItemResponse;
import com.smartseason.inventory.web.dto.StockItemUpdateRequest;
import com.smartseason.inventory.platform.ListFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StockItemService {

    private static final String RESOURCE = "StockItem";
    private static final String ENTITY = "stock_items";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("warehouseId", UUID.class),
            Map.entry("commodityCode", String.class),
            Map.entry("grade", String.class),
            Map.entry("batchId", UUID.class),
            Map.entry("unit", String.class));

    private static final List<String> SEARCHABLE = List.of("commodityCode", "grade", "unit");

    private final StockItemRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public StockItemService(StockItemRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<StockItemResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<StockItem>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(StockItemResponse::from));
    }

    public PageResponse<StockItemResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(StockItemResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<StockItemResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<StockItem> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(StockItemResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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
        counts.invalidate(ENTITY, saved.getTenantId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "StockItemDeleted", id, null);
    }

    private StockItem require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
