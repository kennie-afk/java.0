package com.smartseason.inventory.service;

import com.smartseason.inventory.domain.Batch;
import com.smartseason.inventory.platform.CountCache;
import com.smartseason.inventory.platform.CountCache;
import com.smartseason.inventory.platform.EventPublisher;
import com.smartseason.inventory.platform.Cursor;
import com.smartseason.inventory.platform.CursorPage;
import com.smartseason.inventory.platform.PageResponse;
import com.smartseason.inventory.platform.ResourceNotFoundException;
import com.smartseason.inventory.platform.TenantContext;
import com.smartseason.inventory.repo.BatchRepository;
import com.smartseason.inventory.web.dto.BatchCreateRequest;
import com.smartseason.inventory.web.dto.BatchResponse;
import com.smartseason.inventory.web.dto.BatchUpdateRequest;
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
public class BatchService {

    private static final String RESOURCE = "Batch";
    private static final String ENTITY = "batches";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("batchCode", String.class),
            Map.entry("commodityCode", String.class),
            Map.entry("farmId", UUID.class),
            Map.entry("plotId", UUID.class),
            Map.entry("seasonId", UUID.class),
            Map.entry("warehouseId", UUID.class),
            Map.entry("grade", String.class),
            Map.entry("status", Batch.Status.class));

    private static final List<String> SEARCHABLE = List.of("batchCode", "commodityCode", "grade");

    private final BatchRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public BatchService(BatchRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<BatchResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<Batch>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(BatchResponse::from));
    }

    public PageResponse<BatchResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(BatchResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<BatchResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<Batch> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(BatchResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public BatchResponse get(UUID id) {
        return BatchResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public BatchResponse create(BatchCreateRequest request) {
        Batch entity = new Batch();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setBatchCode(request.batchCode());
        entity.setCommodityCode(request.commodityCode());
        entity.setFarmId(request.farmId());
        entity.setPlotId(request.plotId());
        entity.setSeasonId(request.seasonId());
        entity.setHarvestedOn(request.harvestedOn());
        entity.setReceivedAt(request.receivedAt());
        entity.setWarehouseId(request.warehouseId());
        entity.setGrossWeightKg(request.grossWeightKg());
        entity.setNetWeightKg(request.netWeightKg());
        entity.setGrade(request.grade());
        entity.setMoisturePct(request.moisturePct());
        entity.setStatus(request.status());

        Batch saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "BatchCreated", saved.getId(), BatchResponse.from(saved));
        return BatchResponse.from(saved);
    }

    @Transactional
    public BatchResponse update(UUID id, BatchUpdateRequest request) {
        Batch entity = require(id);
        if (request.batchCode() != null) {
            entity.setBatchCode(request.batchCode());
        }
        if (request.commodityCode() != null) {
            entity.setCommodityCode(request.commodityCode());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.seasonId() != null) {
            entity.setSeasonId(request.seasonId());
        }
        if (request.harvestedOn() != null) {
            entity.setHarvestedOn(request.harvestedOn());
        }
        if (request.receivedAt() != null) {
            entity.setReceivedAt(request.receivedAt());
        }
        if (request.warehouseId() != null) {
            entity.setWarehouseId(request.warehouseId());
        }
        if (request.grossWeightKg() != null) {
            entity.setGrossWeightKg(request.grossWeightKg());
        }
        if (request.netWeightKg() != null) {
            entity.setNetWeightKg(request.netWeightKg());
        }
        if (request.grade() != null) {
            entity.setGrade(request.grade());
        }
        if (request.moisturePct() != null) {
            entity.setMoisturePct(request.moisturePct());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        Batch saved = repository.save(entity);
        events.publish("market", "BatchUpdated", saved.getId(), BatchResponse.from(saved));
        return BatchResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Batch entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "BatchDeleted", id, null);
    }

    private Batch require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
