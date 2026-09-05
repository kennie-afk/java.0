package com.smartseason.identity.service;

import com.smartseason.identity.domain.KycRecord;
import com.smartseason.identity.platform.EventPublisher;
import com.smartseason.identity.platform.PageResponse;
import com.smartseason.identity.platform.ResourceNotFoundException;
import com.smartseason.identity.platform.TenantContext;
import com.smartseason.identity.repo.KycRecordRepository;
import com.smartseason.identity.web.dto.KycRecordCreateRequest;
import com.smartseason.identity.web.dto.KycRecordResponse;
import com.smartseason.identity.web.dto.KycRecordUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class KycRecordService {

    private static final String RESOURCE = "KycRecord";

    private final KycRecordRepository repository;
    private final EventPublisher events;

    public KycRecordService(KycRecordRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<KycRecordResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(KycRecordResponse::from));
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
        events.publish("identity", "KycRecordDeleted", id, null);
    }

    private KycRecord require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
