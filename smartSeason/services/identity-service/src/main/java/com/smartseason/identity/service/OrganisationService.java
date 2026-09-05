package com.smartseason.identity.service;

import com.smartseason.identity.domain.Organisation;
import com.smartseason.identity.platform.EventPublisher;
import com.smartseason.identity.platform.PageResponse;
import com.smartseason.identity.platform.ResourceNotFoundException;
import com.smartseason.identity.platform.TenantContext;
import com.smartseason.identity.repo.OrganisationRepository;
import com.smartseason.identity.web.dto.OrganisationCreateRequest;
import com.smartseason.identity.web.dto.OrganisationResponse;
import com.smartseason.identity.web.dto.OrganisationUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrganisationService {

    private static final String RESOURCE = "Organisation";

    private final OrganisationRepository repository;
    private final EventPublisher events;

    public OrganisationService(OrganisationRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<OrganisationResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(OrganisationResponse::from));
    }

    public OrganisationResponse get(UUID id) {
        return OrganisationResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public OrganisationResponse create(OrganisationCreateRequest request) {
        Organisation entity = new Organisation();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setName(request.name());
        entity.setOrgType(request.orgType());
        entity.setCounty(request.county());
        entity.setRegistrationNo(request.registrationNo());
        entity.setPhone(request.phone());
        entity.setEmail(request.email());
        entity.setStatus(request.status());
        entity.setKycStatus(request.kycStatus());

        Organisation saved = repository.save(entity);
        events.publish("identity", "OrganisationCreated", saved.getId(), OrganisationResponse.from(saved));
        return OrganisationResponse.from(saved);
    }

    @Transactional
    public OrganisationResponse update(UUID id, OrganisationUpdateRequest request) {
        Organisation entity = require(id);
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.orgType() != null) {
            entity.setOrgType(request.orgType());
        }
        if (request.county() != null) {
            entity.setCounty(request.county());
        }
        if (request.registrationNo() != null) {
            entity.setRegistrationNo(request.registrationNo());
        }
        if (request.phone() != null) {
            entity.setPhone(request.phone());
        }
        if (request.email() != null) {
            entity.setEmail(request.email());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.kycStatus() != null) {
            entity.setKycStatus(request.kycStatus());
        }

        Organisation saved = repository.save(entity);
        events.publish("identity", "OrganisationUpdated", saved.getId(), OrganisationResponse.from(saved));
        return OrganisationResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Organisation entity = require(id);
        repository.delete(entity);
        events.publish("identity", "OrganisationDeleted", id, null);
    }

    private Organisation require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
