package com.smartseason.farm.service;

import com.smartseason.farm.domain.SoilProfile;
import com.smartseason.farm.platform.EventPublisher;
import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.platform.ResourceNotFoundException;
import com.smartseason.farm.platform.TenantContext;
import com.smartseason.farm.repo.SoilProfileRepository;
import com.smartseason.farm.web.dto.SoilProfileCreateRequest;
import com.smartseason.farm.web.dto.SoilProfileResponse;
import com.smartseason.farm.web.dto.SoilProfileUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SoilProfileService {

    private static final String RESOURCE = "SoilProfile";

    private final SoilProfileRepository repository;
    private final EventPublisher events;

    public SoilProfileService(SoilProfileRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<SoilProfileResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(SoilProfileResponse::from));
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
        events.publish("farm", "SoilProfileDeleted", id, null);
    }

    private SoilProfile require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
