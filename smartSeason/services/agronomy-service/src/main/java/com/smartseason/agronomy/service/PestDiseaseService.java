package com.smartseason.agronomy.service;

import com.smartseason.agronomy.domain.PestDisease;
import com.smartseason.agronomy.platform.EventPublisher;
import com.smartseason.agronomy.platform.PageResponse;
import com.smartseason.agronomy.platform.ResourceNotFoundException;
import com.smartseason.agronomy.platform.TenantContext;
import com.smartseason.agronomy.repo.PestDiseaseRepository;
import com.smartseason.agronomy.web.dto.PestDiseaseCreateRequest;
import com.smartseason.agronomy.web.dto.PestDiseaseResponse;
import com.smartseason.agronomy.web.dto.PestDiseaseUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PestDiseaseService {

    private static final String RESOURCE = "PestDisease";

    private final PestDiseaseRepository repository;
    private final EventPublisher events;

    public PestDiseaseService(PestDiseaseRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<PestDiseaseResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(PestDiseaseResponse::from));
    }

    public PestDiseaseResponse get(UUID id) {
        return PestDiseaseResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public PestDiseaseResponse create(PestDiseaseCreateRequest request) {
        PestDisease entity = new PestDisease();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCode(request.code());
        entity.setCommonName(request.commonName());
        entity.setScientificName(request.scientificName());
        entity.setType(request.type());
        entity.setAffectedCrops(request.affectedCrops());
        entity.setSymptoms(request.symptoms());
        entity.setManagement(request.management());
        entity.setImageUrl(request.imageUrl());

        PestDisease saved = repository.save(entity);
        events.publish("farm", "PestDiseaseCreated", saved.getId(), PestDiseaseResponse.from(saved));
        return PestDiseaseResponse.from(saved);
    }

    @Transactional
    public PestDiseaseResponse update(UUID id, PestDiseaseUpdateRequest request) {
        PestDisease entity = require(id);
        if (request.code() != null) {
            entity.setCode(request.code());
        }
        if (request.commonName() != null) {
            entity.setCommonName(request.commonName());
        }
        if (request.scientificName() != null) {
            entity.setScientificName(request.scientificName());
        }
        if (request.type() != null) {
            entity.setType(request.type());
        }
        if (request.affectedCrops() != null) {
            entity.setAffectedCrops(request.affectedCrops());
        }
        if (request.symptoms() != null) {
            entity.setSymptoms(request.symptoms());
        }
        if (request.management() != null) {
            entity.setManagement(request.management());
        }
        if (request.imageUrl() != null) {
            entity.setImageUrl(request.imageUrl());
        }

        PestDisease saved = repository.save(entity);
        events.publish("farm", "PestDiseaseUpdated", saved.getId(), PestDiseaseResponse.from(saved));
        return PestDiseaseResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        PestDisease entity = require(id);
        repository.delete(entity);
        events.publish("farm", "PestDiseaseDeleted", id, null);
    }

    private PestDisease require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
