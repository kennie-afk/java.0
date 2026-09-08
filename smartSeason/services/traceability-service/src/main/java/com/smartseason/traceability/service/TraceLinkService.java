package com.smartseason.traceability.service;

import com.smartseason.traceability.domain.TraceLink;
import com.smartseason.traceability.platform.CountCache;
import com.smartseason.traceability.platform.CountCache;
import com.smartseason.traceability.platform.EventPublisher;
import com.smartseason.traceability.platform.PageResponse;
import com.smartseason.traceability.platform.ResourceNotFoundException;
import com.smartseason.traceability.platform.TenantContext;
import com.smartseason.traceability.repo.TraceLinkRepository;
import com.smartseason.traceability.web.dto.TraceLinkCreateRequest;
import com.smartseason.traceability.web.dto.TraceLinkResponse;
import com.smartseason.traceability.web.dto.TraceLinkUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TraceLinkService {

    private static final String RESOURCE = "TraceLink";
    private static final String ENTITY = "trace_links";

    private final TraceLinkRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public TraceLinkService(TraceLinkRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<TraceLinkResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(TraceLinkResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public TraceLinkResponse get(UUID id) {
        return TraceLinkResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public TraceLinkResponse create(TraceLinkCreateRequest request) {
        TraceLink entity = new TraceLink();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setBatchCode(request.batchCode());
        entity.setSequence(request.sequence());
        entity.setNodeType(request.nodeType());
        entity.setNodeRef(request.nodeRef());
        entity.setOccurredAt(request.occurredAt());
        entity.setActorOrgId(request.actorOrgId());
        entity.setLocation(request.location());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setAttributes(request.attributes());
        entity.setEvidenceUrl(request.evidenceUrl());

        TraceLink saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("platform", "TraceLinkCreated", saved.getId(), TraceLinkResponse.from(saved));
        return TraceLinkResponse.from(saved);
    }

    @Transactional
    public TraceLinkResponse update(UUID id, TraceLinkUpdateRequest request) {
        TraceLink entity = require(id);
        if (request.batchCode() != null) {
            entity.setBatchCode(request.batchCode());
        }
        if (request.sequence() != null) {
            entity.setSequence(request.sequence());
        }
        if (request.nodeType() != null) {
            entity.setNodeType(request.nodeType());
        }
        if (request.nodeRef() != null) {
            entity.setNodeRef(request.nodeRef());
        }
        if (request.occurredAt() != null) {
            entity.setOccurredAt(request.occurredAt());
        }
        if (request.actorOrgId() != null) {
            entity.setActorOrgId(request.actorOrgId());
        }
        if (request.location() != null) {
            entity.setLocation(request.location());
        }
        if (request.latitude() != null) {
            entity.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            entity.setLongitude(request.longitude());
        }
        if (request.attributes() != null) {
            entity.setAttributes(request.attributes());
        }
        if (request.evidenceUrl() != null) {
            entity.setEvidenceUrl(request.evidenceUrl());
        }

        TraceLink saved = repository.save(entity);
        events.publish("platform", "TraceLinkUpdated", saved.getId(), TraceLinkResponse.from(saved));
        return TraceLinkResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        TraceLink entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "TraceLinkDeleted", id, null);
    }

    private TraceLink require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
