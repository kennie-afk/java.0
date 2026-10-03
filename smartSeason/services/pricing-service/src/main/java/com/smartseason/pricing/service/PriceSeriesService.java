package com.smartseason.pricing.service;

import com.smartseason.pricing.domain.PriceSeries;
import com.smartseason.pricing.platform.CountCache;
import com.smartseason.pricing.platform.CountCache;
import com.smartseason.pricing.platform.EventPublisher;
import com.smartseason.pricing.platform.ReferenceChecker;
import com.smartseason.pricing.platform.Cursor;
import com.smartseason.pricing.platform.CursorPage;
import com.smartseason.pricing.platform.PageResponse;
import com.smartseason.pricing.platform.ResourceNotFoundException;
import com.smartseason.pricing.platform.TenantContext;
import com.smartseason.pricing.repo.PriceSeriesRepository;
import com.smartseason.pricing.web.dto.PriceSeriesCreateRequest;
import com.smartseason.pricing.web.dto.PriceSeriesResponse;
import com.smartseason.pricing.web.dto.PriceSeriesUpdateRequest;
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
public class PriceSeriesService {

    private static final String RESOURCE = "PriceSeries";
    private static final String ENTITY = "price_series";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("commodityCode", String.class),
            Map.entry("county", String.class),
            Map.entry("marketName", String.class),
            Map.entry("grade", String.class),
            Map.entry("unit", String.class),
            Map.entry("currency", String.class),
            Map.entry("source", String.class));

    private static final List<String> SEARCHABLE = List.of("commodityCode", "county", "marketName", "grade", "unit", "currency", "source");

    private final PriceSeriesRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public PriceSeriesService(PriceSeriesRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<PriceSeriesResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<PriceSeries>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(PriceSeriesResponse::from));
    }

    public PageResponse<PriceSeriesResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(PriceSeriesResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<PriceSeriesResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<PriceSeries> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(PriceSeriesResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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
