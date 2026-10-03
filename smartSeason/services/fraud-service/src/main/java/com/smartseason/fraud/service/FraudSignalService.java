package com.smartseason.fraud.service;

import com.smartseason.fraud.domain.FraudSignal;
import com.smartseason.fraud.platform.CountCache;
import com.smartseason.fraud.platform.CountCache;
import com.smartseason.fraud.platform.EventPublisher;
import com.smartseason.fraud.platform.ReferenceChecker;
import com.smartseason.fraud.platform.Cursor;
import com.smartseason.fraud.platform.CursorPage;
import com.smartseason.fraud.platform.PageResponse;
import com.smartseason.fraud.platform.ResourceNotFoundException;
import com.smartseason.fraud.platform.TenantContext;
import com.smartseason.fraud.repo.FraudSignalRepository;
import com.smartseason.fraud.web.dto.FraudSignalCreateRequest;
import com.smartseason.fraud.web.dto.FraudSignalResponse;
import com.smartseason.fraud.web.dto.FraudSignalUpdateRequest;
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
public class FraudSignalService {

    private static final String RESOURCE = "FraudSignal";
    private static final String ENTITY = "fraud_signals";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("subjectType", FraudSignal.SubjectType.class),
            Map.entry("subjectId", UUID.class),
            Map.entry("ruleCode", String.class),
            Map.entry("typology", String.class),
            Map.entry("sourceEvent", String.class),
            Map.entry("caseId", UUID.class));

    private static final List<String> SEARCHABLE = List.of("ruleCode", "typology", "sourceEvent");

    private final FraudSignalRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public FraudSignalService(FraudSignalRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<FraudSignalResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<FraudSignal>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(FraudSignalResponse::from));
    }

    public PageResponse<FraudSignalResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(FraudSignalResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<FraudSignalResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<FraudSignal> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(FraudSignalResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public FraudSignalResponse get(UUID id) {
        return FraudSignalResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public FraudSignalResponse create(FraudSignalCreateRequest request) {
        FraudSignal entity = new FraudSignal();
        entity.setTenantId(TenantContext.requireTenantId());

        references.require("FraudCase", "caseId", request.caseId());
        entity.setSubjectType(request.subjectType());
        entity.setSubjectId(request.subjectId());
        entity.setRuleCode(request.ruleCode());
        entity.setTypology(request.typology());
        entity.setScore(request.score());
        entity.setDetectedAt(request.detectedAt());
        entity.setSourceEvent(request.sourceEvent());
        entity.setDetails(request.details());
        entity.setCaseId(request.caseId());

        FraudSignal saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("workforce", "FraudSignalCreated", saved.getId(), FraudSignalResponse.from(saved));
        return FraudSignalResponse.from(saved);
    }

    @Transactional
    public FraudSignalResponse update(UUID id, FraudSignalUpdateRequest request) {
        FraudSignal entity = require(id);
        references.require("FraudCase", "caseId", request.caseId());
        if (request.subjectType() != null) {
            entity.setSubjectType(request.subjectType());
        }
        if (request.subjectId() != null) {
            entity.setSubjectId(request.subjectId());
        }
        if (request.ruleCode() != null) {
            entity.setRuleCode(request.ruleCode());
        }
        if (request.typology() != null) {
            entity.setTypology(request.typology());
        }
        if (request.score() != null) {
            entity.setScore(request.score());
        }
        if (request.detectedAt() != null) {
            entity.setDetectedAt(request.detectedAt());
        }
        if (request.sourceEvent() != null) {
            entity.setSourceEvent(request.sourceEvent());
        }
        if (request.details() != null) {
            entity.setDetails(request.details());
        }
        if (request.caseId() != null) {
            entity.setCaseId(request.caseId());
        }

        FraudSignal saved = repository.save(entity);
        events.publish("workforce", "FraudSignalUpdated", saved.getId(), FraudSignalResponse.from(saved));
        return FraudSignalResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        FraudSignal entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("workforce", "FraudSignalDeleted", id, null);
    }

    private FraudSignal require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
