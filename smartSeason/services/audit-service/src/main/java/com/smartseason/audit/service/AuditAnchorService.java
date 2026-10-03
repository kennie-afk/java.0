package com.smartseason.audit.service;

import com.smartseason.audit.domain.AuditAnchor;
import com.smartseason.audit.platform.CountCache;
import com.smartseason.audit.platform.CountCache;
import com.smartseason.audit.platform.EventPublisher;
import com.smartseason.audit.platform.ReferenceChecker;
import com.smartseason.audit.platform.Cursor;
import com.smartseason.audit.platform.CursorPage;
import com.smartseason.audit.platform.PageResponse;
import com.smartseason.audit.platform.ResourceNotFoundException;
import com.smartseason.audit.platform.TenantContext;
import com.smartseason.audit.repo.AuditAnchorRepository;
import com.smartseason.audit.web.dto.AuditAnchorCreateRequest;
import com.smartseason.audit.web.dto.AuditAnchorResponse;
import com.smartseason.audit.web.dto.AuditAnchorUpdateRequest;
import com.smartseason.audit.platform.ListFilter;
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
public class AuditAnchorService {

    private static final String RESOURCE = "AuditAnchor";
    private static final String ENTITY = "audit_anchors";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("externalRef", String.class));

    private static final List<String> SEARCHABLE = List.of("externalRef");

    private final AuditAnchorRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public AuditAnchorService(AuditAnchorRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<AuditAnchorResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<AuditAnchor>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(AuditAnchorResponse::from));
    }

    public PageResponse<AuditAnchorResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(AuditAnchorResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<AuditAnchorResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<AuditAnchor> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(AuditAnchorResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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
        counts.invalidate(ENTITY, saved.getTenantId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "AuditAnchorDeleted", id, null);
    }

    private AuditAnchor require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
