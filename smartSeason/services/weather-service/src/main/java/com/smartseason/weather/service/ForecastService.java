package com.smartseason.weather.service;

import com.smartseason.weather.domain.Forecast;
import com.smartseason.weather.platform.CountCache;
import com.smartseason.weather.platform.CountCache;
import com.smartseason.weather.platform.EventPublisher;
import com.smartseason.weather.platform.PageResponse;
import com.smartseason.weather.platform.ResourceNotFoundException;
import com.smartseason.weather.platform.TenantContext;
import com.smartseason.weather.repo.ForecastRepository;
import com.smartseason.weather.web.dto.ForecastCreateRequest;
import com.smartseason.weather.web.dto.ForecastResponse;
import com.smartseason.weather.web.dto.ForecastUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ForecastService {

    private static final String RESOURCE = "Forecast";
    private static final String ENTITY = "forecasts";

    private final ForecastRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public ForecastService(ForecastRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<ForecastResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(ForecastResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public ForecastResponse get(UUID id) {
        return ForecastResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ForecastResponse create(ForecastCreateRequest request) {
        Forecast entity = new Forecast();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setGeoCell(request.geoCell());
        entity.setForecastFor(request.forecastFor());
        entity.setIssuedAt(request.issuedAt());
        entity.setTempMinC(request.tempMinC());
        entity.setTempMaxC(request.tempMaxC());
        entity.setRainfallMm(request.rainfallMm());
        entity.setHumidityPct(request.humidityPct());
        entity.setWindKph(request.windKph());
        entity.setConditions(request.conditions());
        entity.setProvider(request.provider());

        Forecast saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("farm", "ForecastCreated", saved.getId(), ForecastResponse.from(saved));
        return ForecastResponse.from(saved);
    }

    @Transactional
    public ForecastResponse update(UUID id, ForecastUpdateRequest request) {
        Forecast entity = require(id);
        if (request.geoCell() != null) {
            entity.setGeoCell(request.geoCell());
        }
        if (request.forecastFor() != null) {
            entity.setForecastFor(request.forecastFor());
        }
        if (request.issuedAt() != null) {
            entity.setIssuedAt(request.issuedAt());
        }
        if (request.tempMinC() != null) {
            entity.setTempMinC(request.tempMinC());
        }
        if (request.tempMaxC() != null) {
            entity.setTempMaxC(request.tempMaxC());
        }
        if (request.rainfallMm() != null) {
            entity.setRainfallMm(request.rainfallMm());
        }
        if (request.humidityPct() != null) {
            entity.setHumidityPct(request.humidityPct());
        }
        if (request.windKph() != null) {
            entity.setWindKph(request.windKph());
        }
        if (request.conditions() != null) {
            entity.setConditions(request.conditions());
        }
        if (request.provider() != null) {
            entity.setProvider(request.provider());
        }

        Forecast saved = repository.save(entity);
        events.publish("farm", "ForecastUpdated", saved.getId(), ForecastResponse.from(saved));
        return ForecastResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Forecast entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "ForecastDeleted", id, null);
    }

    private Forecast require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
