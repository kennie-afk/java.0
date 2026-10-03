package com.smartseason.fraud.service;

import com.smartseason.fraud.domain.FraudEvidence;
import com.smartseason.fraud.platform.CountCache;
import com.smartseason.fraud.platform.CountCache;
import com.smartseason.fraud.platform.EventPublisher;
import com.smartseason.fraud.platform.Cursor;
import com.smartseason.fraud.platform.CursorPage;
import com.smartseason.fraud.platform.PageResponse;
import com.smartseason.fraud.platform.ResourceNotFoundException;
import com.smartseason.fraud.platform.TenantContext;
import com.smartseason.fraud.repo.FraudEvidenceRepository;
import com.smartseason.fraud.web.dto.FraudEvidenceCreateRequest;
import com.smartseason.fraud.web.dto.FraudEvidenceResponse;
import com.smartseason.fraud.web.dto.FraudEvidenceUpdateRequest;
import com.smartseason.fraud.platform.ListFilter;
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
public class FraudEvidenceService {

    private static final String RESOURCE = "FraudEvidence";
    private static final String ENTITY = "fraud_evidence";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("caseId", UUID.class),
            Map.entry("label", String.class),
            Map.entry("evidenceType", String.class));

    private static final List<String> SEARCHABLE = List.of("label", "evidenceType");

    private final FraudEvidenceRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public FraudEvidenceService(FraudEvidenceRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<FraudEvidenceResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<FraudEvidence>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(FraudEvidenceResponse::from));
    }

    public PageResponse<FraudEvidenceResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(FraudEvidenceResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<FraudEvidenceResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<FraudEvidence> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(FraudEvidenceResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public FraudEvidenceResponse get(UUID id) {
        return FraudEvidenceResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public FraudEvidenceResponse create(FraudEvidenceCreateRequest request) {
        FraudEvidence entity = new FraudEvidence();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCaseId(request.caseId());
        entity.setLabel(request.label());
        entity.setEvidenceType(request.evidenceType());
        entity.setPayload(request.payload());
        entity.setWeight(request.weight());
        entity.setCollectedAt(request.collectedAt());

        FraudEvidence saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("workforce", "FraudEvidenceCreated", saved.getId(), FraudEvidenceResponse.from(saved));
        return FraudEvidenceResponse.from(saved);
    }

    @Transactional
    public FraudEvidenceResponse update(UUID id, FraudEvidenceUpdateRequest request) {
        FraudEvidence entity = require(id);
        if (request.caseId() != null) {
            entity.setCaseId(request.caseId());
        }
        if (request.label() != null) {
            entity.setLabel(request.label());
        }
        if (request.evidenceType() != null) {
            entity.setEvidenceType(request.evidenceType());
        }
        if (request.payload() != null) {
            entity.setPayload(request.payload());
        }
        if (request.weight() != null) {
            entity.setWeight(request.weight());
        }
        if (request.collectedAt() != null) {
            entity.setCollectedAt(request.collectedAt());
        }

        FraudEvidence saved = repository.save(entity);
        events.publish("workforce", "FraudEvidenceUpdated", saved.getId(), FraudEvidenceResponse.from(saved));
        return FraudEvidenceResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        FraudEvidence entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("workforce", "FraudEvidenceDeleted", id, null);
    }

    private FraudEvidence require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
