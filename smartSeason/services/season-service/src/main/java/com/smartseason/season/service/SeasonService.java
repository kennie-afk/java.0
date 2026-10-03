package com.smartseason.season.service;

import com.smartseason.season.domain.Season;
import com.smartseason.season.platform.CountCache;
import com.smartseason.season.platform.CountCache;
import com.smartseason.season.platform.EventPublisher;
import com.smartseason.season.platform.Cursor;
import com.smartseason.season.platform.CursorPage;
import com.smartseason.season.platform.PageResponse;
import com.smartseason.season.platform.ResourceNotFoundException;
import com.smartseason.season.platform.TenantContext;
import com.smartseason.season.repo.SeasonRepository;
import com.smartseason.season.web.dto.SeasonCreateRequest;
import com.smartseason.season.web.dto.SeasonResponse;
import com.smartseason.season.web.dto.SeasonUpdateRequest;
import com.smartseason.season.platform.ListFilter;
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
public class SeasonService {

    private static final String RESOURCE = "Season";
    private static final String ENTITY = "seasons";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("plotId", UUID.class),
            Map.entry("farmId", UUID.class),
            Map.entry("cropCode", String.class),
            Map.entry("variety", String.class),
            Map.entry("currentStage", String.class),
            Map.entry("status", Season.Status.class));

    private static final List<String> SEARCHABLE = List.of("cropCode", "variety", "currentStage");

    private final SeasonRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public SeasonService(SeasonRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<SeasonResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<Season>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(SeasonResponse::from));
    }

    public PageResponse<SeasonResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(SeasonResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<SeasonResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<Season> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(SeasonResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public SeasonResponse get(UUID id) {
        return SeasonResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public SeasonResponse create(SeasonCreateRequest request) {
        Season entity = new Season();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setPlotId(request.plotId());
        entity.setFarmId(request.farmId());
        entity.setCropCode(request.cropCode());
        entity.setVariety(request.variety());
        entity.setStartDate(request.startDate());
        entity.setExpectedHarvestDate(request.expectedHarvestDate());
        entity.setActualHarvestDate(request.actualHarvestDate());
        entity.setExpectedYieldKg(request.expectedYieldKg());
        entity.setActualYieldKg(request.actualYieldKg());
        entity.setCurrentStage(request.currentStage());
        entity.setStatus(request.status());

        Season saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("farm", "SeasonCreated", saved.getId(), SeasonResponse.from(saved));
        return SeasonResponse.from(saved);
    }

    @Transactional
    public SeasonResponse update(UUID id, SeasonUpdateRequest request) {
        Season entity = require(id);
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.cropCode() != null) {
            entity.setCropCode(request.cropCode());
        }
        if (request.variety() != null) {
            entity.setVariety(request.variety());
        }
        if (request.startDate() != null) {
            entity.setStartDate(request.startDate());
        }
        if (request.expectedHarvestDate() != null) {
            entity.setExpectedHarvestDate(request.expectedHarvestDate());
        }
        if (request.actualHarvestDate() != null) {
            entity.setActualHarvestDate(request.actualHarvestDate());
        }
        if (request.expectedYieldKg() != null) {
            entity.setExpectedYieldKg(request.expectedYieldKg());
        }
        if (request.actualYieldKg() != null) {
            entity.setActualYieldKg(request.actualYieldKg());
        }
        if (request.currentStage() != null) {
            entity.setCurrentStage(request.currentStage());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        Season saved = repository.save(entity);
        events.publish("farm", "SeasonUpdated", saved.getId(), SeasonResponse.from(saved));
        return SeasonResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Season entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "SeasonDeleted", id, null);
    }

    private Season require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
