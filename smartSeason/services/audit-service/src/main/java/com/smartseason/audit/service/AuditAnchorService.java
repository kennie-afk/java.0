package com.smartseason.audit.service;

import com.smartseason.audit.domain.AuditAnchor;
import com.smartseason.audit.platform.EventPublisher;
import com.smartseason.audit.platform.PageResponse;
import com.smartseason.audit.platform.ResourceNotFoundException;
import com.smartseason.audit.platform.TenantContext;
import com.smartseason.audit.repo.AuditAnchorRepository;
import com.smartseason.audit.web.dto.AuditAnchorCreateRequest;
import com.smartseason.audit.web.dto.AuditAnchorResponse;
import com.smartseason.audit.web.dto.AuditAnchorUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditAnchorService {

    private static final String RESOURCE = "AuditAnchor";

    private final AuditAnchorRepository repository;
    private final EventPublisher events;

    public AuditAnchorService(AuditAnchorRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<AuditAnchorResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(AuditAnchorResponse::from));
    }

    public AuditAnchorResponse get(UUID id) {
        return AuditAnchorResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public AuditAnchorResponse create(AuditAnchorCreateRequest request) {
        AuditAnchor entity = new AuditAnchor();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setAnchorSequence(request.anchorSequence());
        entity.setChainHash(request.chainHash());
        entity.setRecordCount(request.recordCount());
        entity.setAnchoredAt(request.anchoredAt());
        entity.setExternalRef(request.externalRef());

        AuditAnchor saved = repository.save(entity);
        events.publish("platform", "AuditAnchorCreated", saved.getId(), AuditAnchorResponse.from(saved));
        return AuditAnchorResponse.from(saved);
    }

    @Transactional
    public AuditAnchorResponse update(UUID id, AuditAnchorUpdateRequest request) {
        AuditAnchor entity = require(id);
        if (request.anchorSequence() != null) {
            entity.setAnchorSequence(request.anchorSequence());
        }
        if (request.chainHash() != null) {
            entity.setChainHash(request.chainHash());
        }
        if (request.recordCount() != null) {
            entity.setRecordCount(request.recordCount());
        }
        if (request.anchoredAt() != null) {
            entity.setAnchoredAt(request.anchoredAt());
        }
        if (request.externalRef() != null) {
            entity.setExternalRef(request.externalRef());
        }

        AuditAnchor saved = repository.save(entity);
        events.publish("platform", "AuditAnchorUpdated", saved.getId(), AuditAnchorResponse.from(saved));
        return AuditAnchorResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        AuditAnchor entity = require(id);
        repository.delete(entity);
        events.publish("platform", "AuditAnchorDeleted", id, null);
    }

    private AuditAnchor require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
