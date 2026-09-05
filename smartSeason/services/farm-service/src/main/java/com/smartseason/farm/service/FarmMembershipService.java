package com.smartseason.farm.service;

import com.smartseason.farm.domain.FarmMembership;
import com.smartseason.farm.platform.EventPublisher;
import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.platform.ResourceNotFoundException;
import com.smartseason.farm.platform.TenantContext;
import com.smartseason.farm.repo.FarmMembershipRepository;
import com.smartseason.farm.web.dto.FarmMembershipCreateRequest;
import com.smartseason.farm.web.dto.FarmMembershipResponse;
import com.smartseason.farm.web.dto.FarmMembershipUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FarmMembershipService {

    private static final String RESOURCE = "FarmMembership";

    private final FarmMembershipRepository repository;
    private final EventPublisher events;

    public FarmMembershipService(FarmMembershipRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<FarmMembershipResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(FarmMembershipResponse::from));
    }

    public FarmMembershipResponse get(UUID id) {
        return FarmMembershipResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public FarmMembershipResponse create(FarmMembershipCreateRequest request) {
        FarmMembership entity = new FarmMembership();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setFarmId(request.farmId());
        entity.setUserId(request.userId());
        entity.setRole(request.role());
        entity.setInvitedBy(request.invitedBy());
        entity.setAcceptedAt(request.acceptedAt());
        entity.setStatus(request.status());

        FarmMembership saved = repository.save(entity);
        events.publish("farm", "FarmMembershipCreated", saved.getId(), FarmMembershipResponse.from(saved));
        return FarmMembershipResponse.from(saved);
    }

    @Transactional
    public FarmMembershipResponse update(UUID id, FarmMembershipUpdateRequest request) {
        FarmMembership entity = require(id);
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.userId() != null) {
            entity.setUserId(request.userId());
        }
        if (request.role() != null) {
            entity.setRole(request.role());
        }
        if (request.invitedBy() != null) {
            entity.setInvitedBy(request.invitedBy());
        }
        if (request.acceptedAt() != null) {
            entity.setAcceptedAt(request.acceptedAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        FarmMembership saved = repository.save(entity);
        events.publish("farm", "FarmMembershipUpdated", saved.getId(), FarmMembershipResponse.from(saved));
        return FarmMembershipResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        FarmMembership entity = require(id);
        repository.delete(entity);
        events.publish("farm", "FarmMembershipDeleted", id, null);
    }

    private FarmMembership require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
