package com.smartseason.workforce.service;

import com.smartseason.workforce.domain.Gang;
import com.smartseason.workforce.platform.EventPublisher;
import com.smartseason.workforce.platform.PageResponse;
import com.smartseason.workforce.platform.ResourceNotFoundException;
import com.smartseason.workforce.platform.TenantContext;
import com.smartseason.workforce.repo.GangRepository;
import com.smartseason.workforce.web.dto.GangCreateRequest;
import com.smartseason.workforce.web.dto.GangResponse;
import com.smartseason.workforce.web.dto.GangUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GangService {

    private static final String RESOURCE = "Gang";

    private final GangRepository repository;
    private final EventPublisher events;

    public GangService(GangRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<GangResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(GangResponse::from));
    }

    public GangResponse get(UUID id) {
        return GangResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public GangResponse create(GangCreateRequest request) {
        Gang entity = new Gang();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setName(request.name());
        entity.setFarmId(request.farmId());
        entity.setSupervisorId(request.supervisorId());
        entity.setTargetSize(request.targetSize());
        entity.setStatus(request.status());
        entity.setNotes(request.notes());

        Gang saved = repository.save(entity);
        events.publish("workforce", "GangCreated", saved.getId(), GangResponse.from(saved));
        return GangResponse.from(saved);
    }

    @Transactional
    public GangResponse update(UUID id, GangUpdateRequest request) {
        Gang entity = require(id);
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.supervisorId() != null) {
            entity.setSupervisorId(request.supervisorId());
        }
        if (request.targetSize() != null) {
            entity.setTargetSize(request.targetSize());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }

        Gang saved = repository.save(entity);
        events.publish("workforce", "GangUpdated", saved.getId(), GangResponse.from(saved));
        return GangResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Gang entity = require(id);
        repository.delete(entity);
        events.publish("workforce", "GangDeleted", id, null);
    }

    private Gang require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
