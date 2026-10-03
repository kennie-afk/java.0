package com.smartseason.fraud.service;

import com.smartseason.fraud.domain.FraudCase;
import com.smartseason.fraud.platform.CountCache;
import com.smartseason.fraud.platform.CountCache;
import com.smartseason.fraud.platform.EventPublisher;
import com.smartseason.fraud.platform.Cursor;
import com.smartseason.fraud.platform.CursorPage;
import com.smartseason.fraud.platform.PageResponse;
import com.smartseason.fraud.platform.ResourceNotFoundException;
import com.smartseason.fraud.platform.TenantContext;
import com.smartseason.fraud.repo.FraudCaseRepository;
import com.smartseason.fraud.web.dto.FraudCaseCreateRequest;
import com.smartseason.fraud.web.dto.FraudCaseResponse;
import com.smartseason.fraud.web.dto.FraudCaseUpdateRequest;
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
public class FraudCaseService {

    private static final String RESOURCE = "FraudCase";
    private static final String ENTITY = "fraud_cases";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("caseNumber", String.class),
            Map.entry("subjectType", FraudCase.SubjectType.class),
            Map.entry("subjectId", UUID.class),
            Map.entry("farmId", UUID.class),
            Map.entry("typology", String.class),
            Map.entry("severity", FraudCase.Severity.class),
            Map.entry("status", FraudCase.Status.class),
            Map.entry("assignedTo", UUID.class),
            Map.entry("payoutHeld", Boolean.class),
            Map.entry("appealOutcome", String.class));

    private static final List<String> SEARCHABLE = List.of("caseNumber", "typology", "appealOutcome");

    private final FraudCaseRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public FraudCaseService(FraudCaseRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<FraudCaseResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<FraudCase>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(FraudCaseResponse::from));
    }

    public PageResponse<FraudCaseResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(FraudCaseResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<FraudCaseResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<FraudCase> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(FraudCaseResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public FraudCaseResponse get(UUID id) {
        return FraudCaseResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public FraudCaseResponse create(FraudCaseCreateRequest request) {
        FraudCase entity = new FraudCase();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCaseNumber(request.caseNumber());
        entity.setSubjectType(request.subjectType());
        entity.setSubjectId(request.subjectId());
        entity.setFarmId(request.farmId());
        entity.setTypology(request.typology());
        entity.setSeverity(request.severity());
        entity.setConfidence(request.confidence());
        entity.setOpenedAt(request.openedAt());
        entity.setStatus(request.status());
        entity.setAssignedTo(request.assignedTo());
        entity.setResolvedAt(request.resolvedAt());
        entity.setResolution(request.resolution());
        entity.setPayoutHeld(request.payoutHeld());
        entity.setAppealedAt(request.appealedAt());
        entity.setAppealOutcome(request.appealOutcome());

        FraudCase saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("workforce", "FraudCaseCreated", saved.getId(), FraudCaseResponse.from(saved));
        return FraudCaseResponse.from(saved);
    }

    @Transactional
    public FraudCaseResponse update(UUID id, FraudCaseUpdateRequest request) {
        FraudCase entity = require(id);
        if (request.caseNumber() != null) {
            entity.setCaseNumber(request.caseNumber());
        }
        if (request.subjectType() != null) {
            entity.setSubjectType(request.subjectType());
        }
        if (request.subjectId() != null) {
            entity.setSubjectId(request.subjectId());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.typology() != null) {
            entity.setTypology(request.typology());
        }
        if (request.severity() != null) {
            entity.setSeverity(request.severity());
        }
        if (request.confidence() != null) {
            entity.setConfidence(request.confidence());
        }
        if (request.openedAt() != null) {
            entity.setOpenedAt(request.openedAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.assignedTo() != null) {
            entity.setAssignedTo(request.assignedTo());
        }
        if (request.resolvedAt() != null) {
            entity.setResolvedAt(request.resolvedAt());
        }
        if (request.resolution() != null) {
            entity.setResolution(request.resolution());
        }
        if (request.payoutHeld() != null) {
            entity.setPayoutHeld(request.payoutHeld());
        }
        if (request.appealedAt() != null) {
            entity.setAppealedAt(request.appealedAt());
        }
        if (request.appealOutcome() != null) {
            entity.setAppealOutcome(request.appealOutcome());
        }

        FraudCase saved = repository.save(entity);
        events.publish("workforce", "FraudCaseUpdated", saved.getId(), FraudCaseResponse.from(saved));
        return FraudCaseResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        FraudCase entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("workforce", "FraudCaseDeleted", id, null);
    }

    private FraudCase require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
