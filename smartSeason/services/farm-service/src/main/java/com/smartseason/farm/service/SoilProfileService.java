package com.smartseason.farm.service;

import com.smartseason.farm.domain.SoilProfile;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.EventPublisher;
import com.smartseason.farm.platform.Cursor;
import com.smartseason.farm.platform.CursorPage;
import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.platform.ResourceNotFoundException;
import com.smartseason.farm.platform.TenantContext;
import com.smartseason.farm.repo.SoilProfileRepository;
import com.smartseason.farm.web.dto.SoilProfileCreateRequest;
import com.smartseason.farm.web.dto.SoilProfileResponse;
import com.smartseason.farm.web.dto.SoilProfileUpdateRequest;
import com.smartseason.farm.platform.ListFilter;
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
public class SoilProfileService {

    private static final String RESOURCE = "SoilProfile";
    private static final String ENTITY = "soil_profiles";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("plotId", UUID.class),
            Map.entry("texture", String.class),
            Map.entry("labName", String.class),
            Map.entry("reportUrl", String.class));

    private static final List<String> SEARCHABLE = List.of("texture", "labName", "reportUrl");

    private final SoilProfileRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public SoilProfileService(SoilProfileRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<SoilProfileResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<SoilProfile>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(SoilProfileResponse::from));
    }

    public PageResponse<SoilProfileResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(SoilProfileResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<SoilProfileResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<SoilProfile> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(SoilProfileResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public SoilProfileResponse get(UUID id) {
        return SoilProfileResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public SoilProfileResponse create(SoilProfileCreateRequest request) {
        SoilProfile entity = new SoilProfile();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setPlotId(request.plotId());
        entity.setSampledAt(request.sampledAt());
        entity.setPh(request.ph());
        entity.setNitrogenPpm(request.nitrogenPpm());
        entity.setPhosphorusPpm(request.phosphorusPpm());
        entity.setPotassiumPpm(request.potassiumPpm());
        entity.setOrganicCarbonPct(request.organicCarbonPct());
        entity.setTexture(request.texture());
        entity.setLabName(request.labName());
        entity.setReportUrl(request.reportUrl());

        SoilProfile saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("farm", "SoilProfileCreated", saved.getId(), SoilProfileResponse.from(saved));
        return SoilProfileResponse.from(saved);
    }

    @Transactional
    public SoilProfileResponse update(UUID id, SoilProfileUpdateRequest request) {
        SoilProfile entity = require(id);
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.sampledAt() != null) {
            entity.setSampledAt(request.sampledAt());
        }
        if (request.ph() != null) {
            entity.setPh(request.ph());
        }
        if (request.nitrogenPpm() != null) {
            entity.setNitrogenPpm(request.nitrogenPpm());
        }
        if (request.phosphorusPpm() != null) {
            entity.setPhosphorusPpm(request.phosphorusPpm());
        }
        if (request.potassiumPpm() != null) {
            entity.setPotassiumPpm(request.potassiumPpm());
        }
        if (request.organicCarbonPct() != null) {
            entity.setOrganicCarbonPct(request.organicCarbonPct());
        }
        if (request.texture() != null) {
            entity.setTexture(request.texture());
        }
        if (request.labName() != null) {
            entity.setLabName(request.labName());
        }
        if (request.reportUrl() != null) {
            entity.setReportUrl(request.reportUrl());
        }

        SoilProfile saved = repository.save(entity);
        events.publish("farm", "SoilProfileUpdated", saved.getId(), SoilProfileResponse.from(saved));
        return SoilProfileResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        SoilProfile entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "SoilProfileDeleted", id, null);
    }

    private SoilProfile require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
