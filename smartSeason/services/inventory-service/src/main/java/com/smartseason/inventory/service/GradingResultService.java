package com.smartseason.inventory.service;

import com.smartseason.inventory.domain.GradingResult;
import com.smartseason.inventory.platform.CountCache;
import com.smartseason.inventory.platform.CountCache;
import com.smartseason.inventory.platform.EventPublisher;
import com.smartseason.inventory.platform.ReferenceChecker;
import com.smartseason.inventory.platform.Cursor;
import com.smartseason.inventory.platform.CursorPage;
import com.smartseason.inventory.platform.PageResponse;
import com.smartseason.inventory.platform.ResourceNotFoundException;
import com.smartseason.inventory.platform.TenantContext;
import com.smartseason.inventory.repo.GradingResultRepository;
import com.smartseason.inventory.web.dto.GradingResultCreateRequest;
import com.smartseason.inventory.web.dto.GradingResultResponse;
import com.smartseason.inventory.web.dto.GradingResultUpdateRequest;
import com.smartseason.inventory.platform.ListFilter;
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
public class GradingResultService {

    private static final String RESOURCE = "GradingResult";
    private static final String ENTITY = "grading_results";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("batchId", UUID.class),
            Map.entry("gradedBy", UUID.class),
            Map.entry("assignedGrade", String.class));

    private static final List<String> SEARCHABLE = List.of("assignedGrade");

    private final GradingResultRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public GradingResultService(GradingResultRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<GradingResultResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<GradingResult>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(GradingResultResponse::from));
    }

    public PageResponse<GradingResultResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(GradingResultResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<GradingResultResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<GradingResult> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(GradingResultResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public GradingResultResponse get(UUID id) {
        return GradingResultResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public GradingResultResponse create(GradingResultCreateRequest request) {
        GradingResult entity = new GradingResult();
        entity.setTenantId(TenantContext.requireTenantId());

        references.require("Batch", "batchId", request.batchId());
        entity.setBatchId(request.batchId());
        entity.setGradedBy(request.gradedBy());
        entity.setGradedAt(request.gradedAt());
        entity.setAssignedGrade(request.assignedGrade());
        entity.setSizeMm(request.sizeMm());
        entity.setDefectPct(request.defectPct());
        entity.setMoisturePct(request.moisturePct());
        entity.setRejectedKg(request.rejectedKg());
        entity.setNotes(request.notes());
        entity.setStandardVersion(request.standardVersion());

        GradingResult saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "GradingResultCreated", saved.getId(), GradingResultResponse.from(saved));
        return GradingResultResponse.from(saved);
    }

    @Transactional
    public GradingResultResponse update(UUID id, GradingResultUpdateRequest request) {
        GradingResult entity = require(id);
        references.require("Batch", "batchId", request.batchId());
        if (request.batchId() != null) {
            entity.setBatchId(request.batchId());
        }
        if (request.gradedBy() != null) {
            entity.setGradedBy(request.gradedBy());
        }
        if (request.gradedAt() != null) {
            entity.setGradedAt(request.gradedAt());
        }
        if (request.assignedGrade() != null) {
            entity.setAssignedGrade(request.assignedGrade());
        }
        if (request.sizeMm() != null) {
            entity.setSizeMm(request.sizeMm());
        }
        if (request.defectPct() != null) {
            entity.setDefectPct(request.defectPct());
        }
        if (request.moisturePct() != null) {
            entity.setMoisturePct(request.moisturePct());
        }
        if (request.rejectedKg() != null) {
            entity.setRejectedKg(request.rejectedKg());
        }
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }
        if (request.standardVersion() != null) {
            entity.setStandardVersion(request.standardVersion());
        }

        GradingResult saved = repository.save(entity);
        events.publish("market", "GradingResultUpdated", saved.getId(), GradingResultResponse.from(saved));
        return GradingResultResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        GradingResult entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "GradingResultDeleted", id, null);
    }

    private GradingResult require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
