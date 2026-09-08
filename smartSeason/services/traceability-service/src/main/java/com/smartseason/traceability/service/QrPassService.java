package com.smartseason.traceability.service;

import com.smartseason.traceability.domain.QrPass;
import com.smartseason.traceability.platform.CountCache;
import com.smartseason.traceability.platform.CountCache;
import com.smartseason.traceability.platform.EventPublisher;
import com.smartseason.traceability.platform.PageResponse;
import com.smartseason.traceability.platform.ResourceNotFoundException;
import com.smartseason.traceability.platform.TenantContext;
import com.smartseason.traceability.repo.QrPassRepository;
import com.smartseason.traceability.web.dto.QrPassCreateRequest;
import com.smartseason.traceability.web.dto.QrPassResponse;
import com.smartseason.traceability.web.dto.QrPassUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class QrPassService {

    private static final String RESOURCE = "QrPass";
    private static final String ENTITY = "qr_passes";

    private final QrPassRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public QrPassService(QrPassRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<QrPassResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(QrPassResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public QrPassResponse get(UUID id) {
        return QrPassResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public QrPassResponse create(QrPassCreateRequest request) {
        QrPass entity = new QrPass();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setBatchCode(request.batchCode());
        entity.setPassCode(request.passCode());
        entity.setQrUrl(request.qrUrl());
        entity.setIssuedAt(request.issuedAt());
        entity.setExpiresAt(request.expiresAt());
        entity.setScanCount(request.scanCount());
        entity.setLastScannedAt(request.lastScannedAt());
        entity.setPublicSummary(request.publicSummary());
        entity.setStatus(request.status());

        QrPass saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("platform", "QrPassCreated", saved.getId(), QrPassResponse.from(saved));
        return QrPassResponse.from(saved);
    }

    @Transactional
    public QrPassResponse update(UUID id, QrPassUpdateRequest request) {
        QrPass entity = require(id);
        if (request.batchCode() != null) {
            entity.setBatchCode(request.batchCode());
        }
        if (request.passCode() != null) {
            entity.setPassCode(request.passCode());
        }
        if (request.qrUrl() != null) {
            entity.setQrUrl(request.qrUrl());
        }
        if (request.issuedAt() != null) {
            entity.setIssuedAt(request.issuedAt());
        }
        if (request.expiresAt() != null) {
            entity.setExpiresAt(request.expiresAt());
        }
        if (request.scanCount() != null) {
            entity.setScanCount(request.scanCount());
        }
        if (request.lastScannedAt() != null) {
            entity.setLastScannedAt(request.lastScannedAt());
        }
        if (request.publicSummary() != null) {
            entity.setPublicSummary(request.publicSummary());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        QrPass saved = repository.save(entity);
        events.publish("platform", "QrPassUpdated", saved.getId(), QrPassResponse.from(saved));
        return QrPassResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        QrPass entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "QrPassDeleted", id, null);
    }

    private QrPass require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
