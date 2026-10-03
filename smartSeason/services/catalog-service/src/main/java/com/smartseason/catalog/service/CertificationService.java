package com.smartseason.catalog.service;

import com.smartseason.catalog.domain.Certification;
import com.smartseason.catalog.platform.CountCache;
import com.smartseason.catalog.platform.CountCache;
import com.smartseason.catalog.platform.EventPublisher;
import com.smartseason.catalog.platform.ReferenceChecker;
import com.smartseason.catalog.platform.Cursor;
import com.smartseason.catalog.platform.CursorPage;
import com.smartseason.catalog.platform.PageResponse;
import com.smartseason.catalog.platform.ResourceNotFoundException;
import com.smartseason.catalog.platform.TenantContext;
import com.smartseason.catalog.repo.CertificationRepository;
import com.smartseason.catalog.web.dto.CertificationCreateRequest;
import com.smartseason.catalog.web.dto.CertificationResponse;
import com.smartseason.catalog.web.dto.CertificationUpdateRequest;
import com.smartseason.catalog.platform.ListFilter;
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
public class CertificationService {

    private static final String RESOURCE = "Certification";
    private static final String ENTITY = "certifications";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("code", String.class),
            Map.entry("name", String.class),
            Map.entry("issuingBody", String.class));

    private static final List<String> SEARCHABLE = List.of("code", "name", "issuingBody");

    private final CertificationRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public CertificationService(CertificationRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<CertificationResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<Certification>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(CertificationResponse::from));
    }

    public PageResponse<CertificationResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(CertificationResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<CertificationResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<Certification> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(CertificationResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public CertificationResponse get(UUID id) {
        return CertificationResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public CertificationResponse create(CertificationCreateRequest request) {
        Certification entity = new Certification();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setIssuingBody(request.issuingBody());
        entity.setDescription(request.description());
        entity.setValidityMonths(request.validityMonths());

        Certification saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "CertificationCreated", saved.getId(), CertificationResponse.from(saved));
        return CertificationResponse.from(saved);
    }

    @Transactional
    public CertificationResponse update(UUID id, CertificationUpdateRequest request) {
        Certification entity = require(id);
        if (request.code() != null) {
            entity.setCode(request.code());
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.issuingBody() != null) {
            entity.setIssuingBody(request.issuingBody());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.validityMonths() != null) {
            entity.setValidityMonths(request.validityMonths());
        }

        Certification saved = repository.save(entity);
        events.publish("market", "CertificationUpdated", saved.getId(), CertificationResponse.from(saved));
        return CertificationResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Certification entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "CertificationDeleted", id, null);
    }

    private Certification require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
