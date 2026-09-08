package com.smartseason.workforce.service;

import com.smartseason.workforce.domain.GangMembership;
import com.smartseason.workforce.platform.CountCache;
import com.smartseason.workforce.platform.CountCache;
import com.smartseason.workforce.platform.EventPublisher;
import com.smartseason.workforce.platform.PageResponse;
import com.smartseason.workforce.platform.ResourceNotFoundException;
import com.smartseason.workforce.platform.TenantContext;
import com.smartseason.workforce.repo.GangMembershipRepository;
import com.smartseason.workforce.web.dto.GangMembershipCreateRequest;
import com.smartseason.workforce.web.dto.GangMembershipResponse;
import com.smartseason.workforce.web.dto.GangMembershipUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GangMembershipService {

    private static final String RESOURCE = "GangMembership";
    private static final String ENTITY = "gang_memberships";

    private final GangMembershipRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public GangMembershipService(GangMembershipRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<GangMembershipResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(GangMembershipResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public GangMembershipResponse get(UUID id) {
        return GangMembershipResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public GangMembershipResponse create(GangMembershipCreateRequest request) {
        GangMembership entity = new GangMembership();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setGangId(request.gangId());
        entity.setWorkerId(request.workerId());
        entity.setJoinedAt(request.joinedAt());
        entity.setLeftAt(request.leftAt());
        entity.setRole(request.role());

        GangMembership saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("workforce", "GangMembershipCreated", saved.getId(), GangMembershipResponse.from(saved));
        return GangMembershipResponse.from(saved);
    }

    @Transactional
    public GangMembershipResponse update(UUID id, GangMembershipUpdateRequest request) {
        GangMembership entity = require(id);
        if (request.gangId() != null) {
            entity.setGangId(request.gangId());
        }
        if (request.workerId() != null) {
            entity.setWorkerId(request.workerId());
        }
        if (request.joinedAt() != null) {
            entity.setJoinedAt(request.joinedAt());
        }
        if (request.leftAt() != null) {
            entity.setLeftAt(request.leftAt());
        }
        if (request.role() != null) {
            entity.setRole(request.role());
        }

        GangMembership saved = repository.save(entity);
        events.publish("workforce", "GangMembershipUpdated", saved.getId(), GangMembershipResponse.from(saved));
        return GangMembershipResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        GangMembership entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("workforce", "GangMembershipDeleted", id, null);
    }

    private GangMembership require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
