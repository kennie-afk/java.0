package com.smartseason.pricing.service;

import com.smartseason.pricing.domain.MarketIndex;
import com.smartseason.pricing.platform.CountCache;
import com.smartseason.pricing.platform.CountCache;
import com.smartseason.pricing.platform.EventPublisher;
import com.smartseason.pricing.platform.Cursor;
import com.smartseason.pricing.platform.CursorPage;
import com.smartseason.pricing.platform.PageResponse;
import com.smartseason.pricing.platform.ResourceNotFoundException;
import com.smartseason.pricing.platform.TenantContext;
import com.smartseason.pricing.repo.MarketIndexRepository;
import com.smartseason.pricing.web.dto.MarketIndexCreateRequest;
import com.smartseason.pricing.web.dto.MarketIndexResponse;
import com.smartseason.pricing.web.dto.MarketIndexUpdateRequest;
import com.smartseason.pricing.platform.ListFilter;
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
public class MarketIndexService {

    private static final String RESOURCE = "MarketIndex";
    private static final String ENTITY = "market_indices";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("commodityCode", String.class),
            Map.entry("region", String.class),
            Map.entry("basis", String.class));

    private static final List<String> SEARCHABLE = List.of("commodityCode", "region", "basis");

    private final MarketIndexRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public MarketIndexService(MarketIndexRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<MarketIndexResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<MarketIndex>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(MarketIndexResponse::from));
    }

    public PageResponse<MarketIndexResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(MarketIndexResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<MarketIndexResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<MarketIndex> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(MarketIndexResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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
