package com.smartseason.audit.service;

import com.smartseason.audit.domain.AuditRecord;
import com.smartseason.audit.platform.CountCache;
import com.smartseason.audit.platform.CountCache;
import com.smartseason.audit.platform.EventPublisher;
import com.smartseason.audit.platform.PageResponse;
import com.smartseason.audit.platform.ResourceNotFoundException;
import com.smartseason.audit.platform.TenantContext;
import com.smartseason.audit.repo.AuditRecordRepository;
import com.smartseason.audit.web.dto.AuditRecordCreateRequest;
import com.smartseason.audit.web.dto.AuditRecordResponse;
import com.smartseason.audit.web.dto.AuditRecordUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditRecordService {

    private static final String RESOURCE = "AuditRecord";
    private static final String ENTITY = "audit_records";

    private final AuditRecordRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public AuditRecordService(AuditRecordRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<AuditRecordResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(AuditRecordResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public AuditRecordResponse get(UUID id) {
        return AuditRecordResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public AuditRecordResponse create(AuditRecordCreateRequest request) {
        AuditRecord entity = new AuditRecord();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setSequence(request.sequence());
        entity.setServiceName(request.serviceName());
        entity.setActorUserId(request.actorUserId());
        entity.setActorRole(request.actorRole());
        entity.setAction(request.action());
        entity.setResourceType(request.resourceType());
        entity.setResourceId(request.resourceId());
        entity.setOutcome(request.outcome());
        entity.setOccurredAt(request.occurredAt());
        entity.setIpAddress(request.ipAddress());
        entity.setUserAgent(request.userAgent());
        entity.setDetails(request.details());
        entity.setPreviousHash(request.previousHash());
        entity.setRecordHash(request.recordHash());

        AuditRecord saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("platform", "AuditRecordCreated", saved.getId(), AuditRecordResponse.from(saved));
        return AuditRecordResponse.from(saved);
    }

    @Transactional
    public AuditRecordResponse update(UUID id, AuditRecordUpdateRequest request) {
        AuditRecord entity = require(id);
        if (request.sequence() != null) {
            entity.setSequence(request.sequence());
        }
        if (request.serviceName() != null) {
            entity.setServiceName(request.serviceName());
        }
        if (request.actorUserId() != null) {
            entity.setActorUserId(request.actorUserId());
        }
        if (request.actorRole() != null) {
            entity.setActorRole(request.actorRole());
        }
        if (request.action() != null) {
            entity.setAction(request.action());
        }
        if (request.resourceType() != null) {
            entity.setResourceType(request.resourceType());
        }
        if (request.resourceId() != null) {
            entity.setResourceId(request.resourceId());
        }
        if (request.outcome() != null) {
            entity.setOutcome(request.outcome());
        }
        if (request.occurredAt() != null) {
            entity.setOccurredAt(request.occurredAt());
        }
        if (request.ipAddress() != null) {
            entity.setIpAddress(request.ipAddress());
        }
        if (request.userAgent() != null) {
            entity.setUserAgent(request.userAgent());
        }
        if (request.details() != null) {
            entity.setDetails(request.details());
        }
        if (request.previousHash() != null) {
            entity.setPreviousHash(request.previousHash());
        }
        if (request.recordHash() != null) {
            entity.setRecordHash(request.recordHash());
        }

        AuditRecord saved = repository.save(entity);
        events.publish("platform", "AuditRecordUpdated", saved.getId(), AuditRecordResponse.from(saved));
        return AuditRecordResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        AuditRecord entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "AuditRecordDeleted", id, null);
    }

    private AuditRecord require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
