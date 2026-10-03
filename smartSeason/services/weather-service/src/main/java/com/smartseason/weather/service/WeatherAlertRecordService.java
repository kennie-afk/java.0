package com.smartseason.weather.service;

import com.smartseason.weather.domain.WeatherAlertRecord;
import com.smartseason.weather.platform.CountCache;
import com.smartseason.weather.platform.CountCache;
import com.smartseason.weather.platform.EventPublisher;
import com.smartseason.weather.platform.ReferenceChecker;
import com.smartseason.weather.platform.Cursor;
import com.smartseason.weather.platform.CursorPage;
import com.smartseason.weather.platform.PageResponse;
import com.smartseason.weather.platform.ResourceNotFoundException;
import com.smartseason.weather.platform.TenantContext;
import com.smartseason.weather.repo.WeatherAlertRecordRepository;
import com.smartseason.weather.web.dto.WeatherAlertRecordCreateRequest;
import com.smartseason.weather.web.dto.WeatherAlertRecordResponse;
import com.smartseason.weather.web.dto.WeatherAlertRecordUpdateRequest;
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
public class WeatherAlertRecordService {

    private static final String RESOURCE = "WeatherAlertRecord";
    private static final String ENTITY = "weather_alerts";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("geoCell", String.class),
            Map.entry("alertType", WeatherAlertRecord.AlertType.class),
            Map.entry("severity", WeatherAlertRecord.Severity.class),
            Map.entry("headline", String.class),
            Map.entry("source", String.class));

    private static final List<String> SEARCHABLE = List.of("geoCell", "headline", "source");

    private final WeatherAlertRecordRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public WeatherAlertRecordService(WeatherAlertRecordRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<WeatherAlertRecordResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<WeatherAlertRecord>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(WeatherAlertRecordResponse::from));
    }

    public PageResponse<WeatherAlertRecordResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(WeatherAlertRecordResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<WeatherAlertRecordResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<WeatherAlertRecord> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(WeatherAlertRecordResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public WeatherAlertRecordResponse get(UUID id) {
        return WeatherAlertRecordResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public WeatherAlertRecordResponse create(WeatherAlertRecordCreateRequest request) {
        WeatherAlertRecord entity = new WeatherAlertRecord();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setGeoCell(request.geoCell());
        entity.setAlertType(request.alertType());
        entity.setSeverity(request.severity());
        entity.setStartsAt(request.startsAt());
        entity.setEndsAt(request.endsAt());
        entity.setHeadline(request.headline());
        entity.setBody(request.body());
        entity.setSource(request.source());

        WeatherAlertRecord saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("farm", "WeatherAlertRecordCreated", saved.getId(), WeatherAlertRecordResponse.from(saved));
        return WeatherAlertRecordResponse.from(saved);
    }

    @Transactional
    public WeatherAlertRecordResponse update(UUID id, WeatherAlertRecordUpdateRequest request) {
        WeatherAlertRecord entity = require(id);
        if (request.geoCell() != null) {
            entity.setGeoCell(request.geoCell());
        }
        if (request.alertType() != null) {
            entity.setAlertType(request.alertType());
        }
        if (request.severity() != null) {
            entity.setSeverity(request.severity());
        }
        if (request.startsAt() != null) {
            entity.setStartsAt(request.startsAt());
        }
        if (request.endsAt() != null) {
            entity.setEndsAt(request.endsAt());
        }
        if (request.headline() != null) {
            entity.setHeadline(request.headline());
        }
        if (request.body() != null) {
            entity.setBody(request.body());
        }
        if (request.source() != null) {
            entity.setSource(request.source());
        }

        WeatherAlertRecord saved = repository.save(entity);
        events.publish("farm", "WeatherAlertRecordUpdated", saved.getId(), WeatherAlertRecordResponse.from(saved));
        return WeatherAlertRecordResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        WeatherAlertRecord entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "WeatherAlertRecordDeleted", id, null);
    }

    private WeatherAlertRecord require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
