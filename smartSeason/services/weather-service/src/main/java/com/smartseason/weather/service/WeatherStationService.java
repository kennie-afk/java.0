package com.smartseason.weather.service;

import com.smartseason.weather.domain.WeatherStation;
import com.smartseason.weather.platform.CountCache;
import com.smartseason.weather.platform.CountCache;
import com.smartseason.weather.platform.EventPublisher;
import com.smartseason.weather.platform.ReferenceChecker;
import com.smartseason.weather.platform.Cursor;
import com.smartseason.weather.platform.CursorPage;
import com.smartseason.weather.platform.PageResponse;
import com.smartseason.weather.platform.ResourceNotFoundException;
import com.smartseason.weather.platform.TenantContext;
import com.smartseason.weather.repo.WeatherStationRepository;
import com.smartseason.weather.web.dto.WeatherStationCreateRequest;
import com.smartseason.weather.web.dto.WeatherStationResponse;
import com.smartseason.weather.web.dto.WeatherStationUpdateRequest;
import com.smartseason.weather.platform.ListFilter;
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
public class WeatherStationService {

    private static final String RESOURCE = "WeatherStation";
    private static final String ENTITY = "weather_stations";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("externalId", String.class),
            Map.entry("name", String.class),
            Map.entry("provider", String.class),
            Map.entry("county", String.class),
            Map.entry("active", Boolean.class));

    private static final List<String> SEARCHABLE = List.of("externalId", "name", "provider", "county");

    private final WeatherStationRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public WeatherStationService(WeatherStationRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<WeatherStationResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<WeatherStation>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(WeatherStationResponse::from));
    }

    public PageResponse<WeatherStationResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(WeatherStationResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<WeatherStationResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<WeatherStation> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(WeatherStationResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public WeatherStationResponse get(UUID id) {
        return WeatherStationResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public WeatherStationResponse create(WeatherStationCreateRequest request) {
        WeatherStation entity = new WeatherStation();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setExternalId(request.externalId());
        entity.setName(request.name());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setElevationM(request.elevationM());
        entity.setProvider(request.provider());
        entity.setCounty(request.county());
        entity.setActive(request.active());

        WeatherStation saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("farm", "WeatherStationCreated", saved.getId(), WeatherStationResponse.from(saved));
        return WeatherStationResponse.from(saved);
    }

    @Transactional
    public WeatherStationResponse update(UUID id, WeatherStationUpdateRequest request) {
        WeatherStation entity = require(id);
        if (request.externalId() != null) {
            entity.setExternalId(request.externalId());
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.latitude() != null) {
            entity.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            entity.setLongitude(request.longitude());
        }
        if (request.elevationM() != null) {
            entity.setElevationM(request.elevationM());
        }
        if (request.provider() != null) {
            entity.setProvider(request.provider());
        }
        if (request.county() != null) {
            entity.setCounty(request.county());
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }

        WeatherStation saved = repository.save(entity);
        events.publish("farm", "WeatherStationUpdated", saved.getId(), WeatherStationResponse.from(saved));
        return WeatherStationResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        WeatherStation entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "WeatherStationDeleted", id, null);
    }

    private WeatherStation require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
