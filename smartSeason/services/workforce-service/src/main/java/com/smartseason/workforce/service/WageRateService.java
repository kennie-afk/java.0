package com.smartseason.workforce.service;

import com.smartseason.workforce.domain.WageRate;
import com.smartseason.workforce.platform.CountCache;
import com.smartseason.workforce.platform.CountCache;
import com.smartseason.workforce.platform.EventPublisher;
import com.smartseason.workforce.platform.Cursor;
import com.smartseason.workforce.platform.CursorPage;
import com.smartseason.workforce.platform.PageResponse;
import com.smartseason.workforce.platform.ResourceNotFoundException;
import com.smartseason.workforce.platform.TenantContext;
import com.smartseason.workforce.repo.WageRateRepository;
import com.smartseason.workforce.web.dto.WageRateCreateRequest;
import com.smartseason.workforce.web.dto.WageRateResponse;
import com.smartseason.workforce.web.dto.WageRateUpdateRequest;
import com.smartseason.workforce.platform.ListFilter;
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
public class WageRateService {

    private static final String RESOURCE = "WageRate";
    private static final String ENTITY = "wage_rates";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("farmId", UUID.class),
            Map.entry("taskCode", String.class),
            Map.entry("rateType", WageRate.RateType.class),
            Map.entry("currency", String.class),
            Map.entry("unit", String.class));

    private static final List<String> SEARCHABLE = List.of("taskCode", "currency", "unit");

    private final WageRateRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public WageRateService(WageRateRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<WageRateResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<WageRate>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(WageRateResponse::from));
    }

    public PageResponse<WageRateResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(WageRateResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<WageRateResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<WageRate> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(WageRateResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public WageRateResponse get(UUID id) {
        return WageRateResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public WageRateResponse create(WageRateCreateRequest request) {
        WageRate entity = new WageRate();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setFarmId(request.farmId());
        entity.setTaskCode(request.taskCode());
        entity.setRateType(request.rateType());
        entity.setAmount(request.amount());
        entity.setCurrency(request.currency());
        entity.setUnit(request.unit());
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setEffectiveTo(request.effectiveTo());

        WageRate saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("workforce", "WageRateCreated", saved.getId(), WageRateResponse.from(saved));
        return WageRateResponse.from(saved);
    }

    @Transactional
    public WageRateResponse update(UUID id, WageRateUpdateRequest request) {
        WageRate entity = require(id);
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.taskCode() != null) {
            entity.setTaskCode(request.taskCode());
        }
        if (request.rateType() != null) {
            entity.setRateType(request.rateType());
        }
        if (request.amount() != null) {
            entity.setAmount(request.amount());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.unit() != null) {
            entity.setUnit(request.unit());
        }
        if (request.effectiveFrom() != null) {
            entity.setEffectiveFrom(request.effectiveFrom());
        }
        if (request.effectiveTo() != null) {
            entity.setEffectiveTo(request.effectiveTo());
        }

        WageRate saved = repository.save(entity);
        events.publish("workforce", "WageRateUpdated", saved.getId(), WageRateResponse.from(saved));
        return WageRateResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        WageRate entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("workforce", "WageRateDeleted", id, null);
    }

    private WageRate require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
