package com.smartseason.identity.service;

import com.smartseason.identity.domain.KycRecord;
import com.smartseason.identity.platform.CountCache;
import com.smartseason.identity.platform.CountCache;
import com.smartseason.identity.platform.EventPublisher;
import com.smartseason.identity.platform.ReferenceChecker;
import com.smartseason.identity.platform.Cursor;
import com.smartseason.identity.platform.CursorPage;
import com.smartseason.identity.platform.PageResponse;
import com.smartseason.identity.platform.ResourceNotFoundException;
import com.smartseason.identity.platform.TenantContext;
import com.smartseason.identity.repo.KycRecordRepository;
import com.smartseason.identity.web.dto.KycRecordCreateRequest;
import com.smartseason.identity.web.dto.KycRecordResponse;
import com.smartseason.identity.web.dto.KycRecordUpdateRequest;
import com.smartseason.identity.platform.ListFilter;
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
public class KycRecordService {

    private static final String RESOURCE = "KycRecord";
    private static final String ENTITY = "kyc_records";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("subjectId", UUID.class),
            Map.entry("subjectType", KycRecord.SubjectType.class),
            Map.entry("idNumber", String.class),
            Map.entry("documentUrl", String.class),
            Map.entry("status", KycRecord.Status.class),
            Map.entry("reviewedBy", UUID.class));

    private static final List<String> SEARCHABLE = List.of("idNumber", "documentUrl");

    private final KycRecordRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public KycRecordService(KycRecordRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<KycRecordResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<KycRecord>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(KycRecordResponse::from));
    }

    public PageResponse<KycRecordResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(KycRecordResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<KycRecordResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<KycRecord> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(KycRecordResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public KycRecordResponse get(UUID id) {
        return KycRecordResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public KycRecordResponse create(KycRecordCreateRequest request) {
        KycRecord entity = new KycRecord();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setSubjectId(request.subjectId());
        entity.setSubjectType(request.subjectType());
        entity.setIdNumber(request.idNumber());
        entity.setDocumentUrl(request.documentUrl());
        entity.setStatus(request.status());
        entity.setReviewedBy(request.reviewedBy());
        entity.setReviewNotes(request.reviewNotes());

        KycRecord saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("identity", "KycRecordCreated", saved.getId(), KycRecordResponse.from(saved));
        return KycRecordResponse.from(saved);
    }

    @Transactional
    public KycRecordResponse update(UUID id, KycRecordUpdateRequest request) {
        KycRecord entity = require(id);
        if (request.subjectId() != null) {
            entity.setSubjectId(request.subjectId());
        }
        if (request.subjectType() != null) {
            entity.setSubjectType(request.subjectType());
        }
        if (request.idNumber() != null) {
            entity.setIdNumber(request.idNumber());
        }
        if (request.documentUrl() != null) {
            entity.setDocumentUrl(request.documentUrl());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.reviewedBy() != null) {
            entity.setReviewedBy(request.reviewedBy());
        }
        if (request.reviewNotes() != null) {
            entity.setReviewNotes(request.reviewNotes());
        }

        KycRecord saved = repository.save(entity);
        events.publish("identity", "KycRecordUpdated", saved.getId(), KycRecordResponse.from(saved));
        return KycRecordResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        KycRecord entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("identity", "KycRecordDeleted", id, null);
    }

    private KycRecord require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
