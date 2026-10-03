package com.smartseason.traceability.service;

import com.smartseason.traceability.domain.TraceBatch;
import com.smartseason.traceability.platform.CountCache;
import com.smartseason.traceability.platform.CountCache;
import com.smartseason.traceability.platform.EventPublisher;
import com.smartseason.traceability.platform.Cursor;
import com.smartseason.traceability.platform.CursorPage;
import com.smartseason.traceability.platform.PageResponse;
import com.smartseason.traceability.platform.ResourceNotFoundException;
import com.smartseason.traceability.platform.TenantContext;
import com.smartseason.traceability.repo.TraceBatchRepository;
import com.smartseason.traceability.web.dto.TraceBatchCreateRequest;
import com.smartseason.traceability.web.dto.TraceBatchResponse;
import com.smartseason.traceability.web.dto.TraceBatchUpdateRequest;
import com.smartseason.traceability.platform.ListFilter;
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
public class TraceBatchService {

    private static final String RESOURCE = "TraceBatch";
    private static final String ENTITY = "trace_batches";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("batchCode", String.class),
            Map.entry("commodityCode", String.class),
            Map.entry("farmId", UUID.class),
            Map.entry("plotId", UUID.class),
            Map.entry("seasonId", UUID.class),
            Map.entry("originCounty", String.class),
            Map.entry("currentHolderOrgId", UUID.class),
            Map.entry("status", TraceBatch.Status.class));

    private static final List<String> SEARCHABLE = List.of("batchCode", "commodityCode", "originCounty");

    private final TraceBatchRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public TraceBatchService(TraceBatchRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<TraceBatchResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<TraceBatch>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(TraceBatchResponse::from));
    }

    public PageResponse<TraceBatchResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(TraceBatchResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<TraceBatchResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<TraceBatch> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(TraceBatchResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public TraceBatchResponse get(UUID id) {
        return TraceBatchResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public TraceBatchResponse create(TraceBatchCreateRequest request) {
        TraceBatch entity = new TraceBatch();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setBatchCode(request.batchCode());
        entity.setCommodityCode(request.commodityCode());
        entity.setFarmId(request.farmId());
        entity.setPlotId(request.plotId());
        entity.setSeasonId(request.seasonId());
        entity.setHarvestedOn(request.harvestedOn());
        entity.setOriginCounty(request.originCounty());
        entity.setCurrentHolderOrgId(request.currentHolderOrgId());
        entity.setQuantityKg(request.quantityKg());
        entity.setStatus(request.status());

        TraceBatch saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("platform", "TraceBatchCreated", saved.getId(), TraceBatchResponse.from(saved));
        return TraceBatchResponse.from(saved);
    }

    @Transactional
    public TraceBatchResponse update(UUID id, TraceBatchUpdateRequest request) {
        TraceBatch entity = require(id);
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
        if (request.originCounty() != null) {
            entity.setOriginCounty(request.originCounty());
        }
        if (request.currentHolderOrgId() != null) {
            entity.setCurrentHolderOrgId(request.currentHolderOrgId());
        }
        if (request.quantityKg() != null) {
            entity.setQuantityKg(request.quantityKg());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        TraceBatch saved = repository.save(entity);
        events.publish("platform", "TraceBatchUpdated", saved.getId(), TraceBatchResponse.from(saved));
        return TraceBatchResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        TraceBatch entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "TraceBatchDeleted", id, null);
    }

    private TraceBatch require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
