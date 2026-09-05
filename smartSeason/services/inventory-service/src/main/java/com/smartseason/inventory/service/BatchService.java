package com.smartseason.inventory.service;

import com.smartseason.inventory.domain.Batch;
import com.smartseason.inventory.platform.EventPublisher;
import com.smartseason.inventory.platform.PageResponse;
import com.smartseason.inventory.platform.ResourceNotFoundException;
import com.smartseason.inventory.platform.TenantContext;
import com.smartseason.inventory.repo.BatchRepository;
import com.smartseason.inventory.web.dto.BatchCreateRequest;
import com.smartseason.inventory.web.dto.BatchResponse;
import com.smartseason.inventory.web.dto.BatchUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BatchService {

    private static final String RESOURCE = "Batch";

    private final BatchRepository repository;
    private final EventPublisher events;

    public BatchService(BatchRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<BatchResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(BatchResponse::from));
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
        events.publish("market", "BatchDeleted", id, null);
    }

    private Batch require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
