package com.smartseason.farm.service;

import com.smartseason.farm.domain.Farm;
import com.smartseason.farm.platform.EventPublisher;
import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.platform.ResourceNotFoundException;
import com.smartseason.farm.platform.TenantContext;
import com.smartseason.farm.repo.FarmRepository;
import com.smartseason.farm.web.dto.FarmCreateRequest;
import com.smartseason.farm.web.dto.FarmResponse;
import com.smartseason.farm.web.dto.FarmUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FarmService {

    private static final String RESOURCE = "Farm";

    private final FarmRepository repository;
    private final EventPublisher events;

    public FarmService(FarmRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<FarmResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(FarmResponse::from));
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
        events.publish("farm", "FarmDeleted", id, null);
    }

    private Farm require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
