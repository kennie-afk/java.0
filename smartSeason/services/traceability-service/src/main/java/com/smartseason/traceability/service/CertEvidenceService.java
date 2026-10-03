package com.smartseason.traceability.service;

import com.smartseason.traceability.domain.CertEvidence;
import com.smartseason.traceability.platform.CountCache;
import com.smartseason.traceability.platform.CountCache;
import com.smartseason.traceability.platform.EventPublisher;
import com.smartseason.traceability.platform.ReferenceChecker;
import com.smartseason.traceability.platform.Cursor;
import com.smartseason.traceability.platform.CursorPage;
import com.smartseason.traceability.platform.PageResponse;
import com.smartseason.traceability.platform.ResourceNotFoundException;
import com.smartseason.traceability.platform.TenantContext;
import com.smartseason.traceability.repo.CertEvidenceRepository;
import com.smartseason.traceability.web.dto.CertEvidenceCreateRequest;
import com.smartseason.traceability.web.dto.CertEvidenceResponse;
import com.smartseason.traceability.web.dto.CertEvidenceUpdateRequest;
import com.smartseason.traceability.platform.ListFilter;
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
public class CertEvidenceService {

    private static final String RESOURCE = "CertEvidence";
    private static final String ENTITY = "cert_evidence";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("batchCode", String.class),
            Map.entry("farmId", UUID.class),
            Map.entry("certificationCode", String.class),
            Map.entry("certificateNo", String.class),
            Map.entry("issuedBy", String.class),
            Map.entry("documentUrl", String.class),
            Map.entry("verified", Boolean.class));

    private static final List<String> SEARCHABLE = List.of("batchCode", "certificationCode", "certificateNo", "issuedBy", "documentUrl");

    private final CertEvidenceRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public CertEvidenceService(CertEvidenceRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<CertEvidenceResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<CertEvidence>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(CertEvidenceResponse::from));
    }

    public PageResponse<CertEvidenceResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(CertEvidenceResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<CertEvidenceResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<CertEvidence> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(CertEvidenceResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public CertEvidenceResponse get(UUID id) {
        return CertEvidenceResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public CertEvidenceResponse create(CertEvidenceCreateRequest request) {
        CertEvidence entity = new CertEvidence();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setBatchCode(request.batchCode());
        entity.setFarmId(request.farmId());
        entity.setCertificationCode(request.certificationCode());
        entity.setCertificateNo(request.certificateNo());
        entity.setIssuedBy(request.issuedBy());
        entity.setIssuedOn(request.issuedOn());
        entity.setExpiresOn(request.expiresOn());
        entity.setDocumentUrl(request.documentUrl());
        entity.setVerified(request.verified());
        entity.setVerifiedAt(request.verifiedAt());

        CertEvidence saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("platform", "CertEvidenceCreated", saved.getId(), CertEvidenceResponse.from(saved));
        return CertEvidenceResponse.from(saved);
    }

    @Transactional
    public CertEvidenceResponse update(UUID id, CertEvidenceUpdateRequest request) {
        CertEvidence entity = require(id);
        if (request.batchCode() != null) {
            entity.setBatchCode(request.batchCode());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.certificationCode() != null) {
            entity.setCertificationCode(request.certificationCode());
        }
        if (request.certificateNo() != null) {
            entity.setCertificateNo(request.certificateNo());
        }
        if (request.issuedBy() != null) {
            entity.setIssuedBy(request.issuedBy());
        }
        if (request.issuedOn() != null) {
            entity.setIssuedOn(request.issuedOn());
        }
        if (request.expiresOn() != null) {
            entity.setExpiresOn(request.expiresOn());
        }
        if (request.documentUrl() != null) {
            entity.setDocumentUrl(request.documentUrl());
        }
        if (request.verified() != null) {
            entity.setVerified(request.verified());
        }
        if (request.verifiedAt() != null) {
            entity.setVerifiedAt(request.verifiedAt());
        }

        CertEvidence saved = repository.save(entity);
        events.publish("platform", "CertEvidenceUpdated", saved.getId(), CertEvidenceResponse.from(saved));
        return CertEvidenceResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        CertEvidence entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "CertEvidenceDeleted", id, null);
    }

    private CertEvidence require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
