package com.smartseason.weather.service;

import com.smartseason.weather.domain.WeatherAlertRecord;
import com.smartseason.weather.platform.EventPublisher;
import com.smartseason.weather.platform.PageResponse;
import com.smartseason.weather.platform.ResourceNotFoundException;
import com.smartseason.weather.platform.TenantContext;
import com.smartseason.weather.repo.WeatherAlertRecordRepository;
import com.smartseason.weather.web.dto.WeatherAlertRecordCreateRequest;
import com.smartseason.weather.web.dto.WeatherAlertRecordResponse;
import com.smartseason.weather.web.dto.WeatherAlertRecordUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WeatherAlertRecordService {

    private static final String RESOURCE = "WeatherAlertRecord";

    private final WeatherAlertRecordRepository repository;
    private final EventPublisher events;

    public WeatherAlertRecordService(WeatherAlertRecordRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<WeatherAlertRecordResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(WeatherAlertRecordResponse::from));
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
        events.publish("farm", "WeatherAlertRecordDeleted", id, null);
    }

    private WeatherAlertRecord require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
