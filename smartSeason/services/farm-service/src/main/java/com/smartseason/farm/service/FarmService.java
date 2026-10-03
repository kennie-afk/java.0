package com.smartseason.farm.service;

import com.smartseason.farm.domain.Farm;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.EventPublisher;
import com.smartseason.farm.platform.Cursor;
import com.smartseason.farm.platform.CursorPage;
import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.platform.ResourceNotFoundException;
import com.smartseason.farm.platform.TenantContext;
import com.smartseason.farm.repo.FarmRepository;
import com.smartseason.farm.web.dto.FarmCreateRequest;
import com.smartseason.farm.web.dto.FarmResponse;
import com.smartseason.farm.web.dto.FarmUpdateRequest;
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
public class FarmService {

    private static final String RESOURCE = "Farm";
    private static final String ENTITY = "farms";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("name", String.class),
            Map.entry("ownerUserId", UUID.class),
            Map.entry("county", String.class),
            Map.entry("subCounty", String.class),
            Map.entry("ward", String.class),
            Map.entry("status", Farm.Status.class),
            Map.entry("cooperativeId", UUID.class),
            Map.entry("registrationNo", String.class));

    private static final List<String> SEARCHABLE = List.of("name", "county", "subCounty", "ward", "registrationNo");

    private final FarmRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public FarmService(FarmRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<FarmResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<Farm>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(FarmResponse::from));
    }

    public PageResponse<FarmResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(FarmResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<FarmResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<Farm> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(FarmResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public FarmResponse get(UUID id) {
        return FarmResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public FarmResponse create(FarmCreateRequest request) {
        Farm entity = new Farm();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setName(request.name());
        entity.setOwnerUserId(request.ownerUserId());
        entity.setCounty(request.county());
        entity.setSubCounty(request.subCounty());
        entity.setWard(request.ward());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setTotalAreaHa(request.totalAreaHa());
        entity.setStatus(request.status());
        entity.setCooperativeId(request.cooperativeId());
        entity.setRegistrationNo(request.registrationNo());

        Farm saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("farm", "FarmCreated", saved.getId(), FarmResponse.from(saved));
        return FarmResponse.from(saved);
    }

    @Transactional
    public FarmResponse update(UUID id, FarmUpdateRequest request) {
        Farm entity = require(id);
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.ownerUserId() != null) {
            entity.setOwnerUserId(request.ownerUserId());
        }
        if (request.county() != null) {
            entity.setCounty(request.county());
        }
        if (request.subCounty() != null) {
            entity.setSubCounty(request.subCounty());
        }
        if (request.ward() != null) {
            entity.setWard(request.ward());
        }
        if (request.latitude() != null) {
            entity.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            entity.setLongitude(request.longitude());
        }
        if (request.totalAreaHa() != null) {
            entity.setTotalAreaHa(request.totalAreaHa());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.cooperativeId() != null) {
            entity.setCooperativeId(request.cooperativeId());
        }
        if (request.registrationNo() != null) {
            entity.setRegistrationNo(request.registrationNo());
        }

        Farm saved = repository.save(entity);
        events.publish("farm", "FarmUpdated", saved.getId(), FarmResponse.from(saved));
        return FarmResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Farm entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "FarmDeleted", id, null);
    }

    private Farm require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
