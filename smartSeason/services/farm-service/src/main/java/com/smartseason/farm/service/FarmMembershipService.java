package com.smartseason.farm.service;

import com.smartseason.farm.domain.FarmMembership;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.EventPublisher;
import com.smartseason.farm.platform.Cursor;
import com.smartseason.farm.platform.CursorPage;
import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.platform.ResourceNotFoundException;
import com.smartseason.farm.platform.TenantContext;
import com.smartseason.farm.repo.FarmMembershipRepository;
import com.smartseason.farm.web.dto.FarmMembershipCreateRequest;
import com.smartseason.farm.web.dto.FarmMembershipResponse;
import com.smartseason.farm.web.dto.FarmMembershipUpdateRequest;
import com.smartseason.farm.platform.ListFilter;
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
public class FarmMembershipService {

    private static final String RESOURCE = "FarmMembership";
    private static final String ENTITY = "farm_memberships";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("farmId", UUID.class),
            Map.entry("userId", UUID.class),
            Map.entry("role", FarmMembership.Role.class),
            Map.entry("invitedBy", UUID.class),
            Map.entry("status", FarmMembership.Status.class));

    private static final List<String> SEARCHABLE = List.of();

    private final FarmMembershipRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public FarmMembershipService(FarmMembershipRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<FarmMembershipResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<FarmMembership>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(FarmMembershipResponse::from));
    }

    public PageResponse<FarmMembershipResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(FarmMembershipResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<FarmMembershipResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<FarmMembership> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(FarmMembershipResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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
        counts.invalidate(ENTITY, saved.getTenantId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "FarmMembershipDeleted", id, null);
    }

    private FarmMembership require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
