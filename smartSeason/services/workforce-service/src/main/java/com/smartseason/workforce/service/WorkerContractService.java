package com.smartseason.workforce.service;

import com.smartseason.workforce.domain.WorkerContract;
import com.smartseason.workforce.platform.CountCache;
import com.smartseason.workforce.platform.CountCache;
import com.smartseason.workforce.platform.EventPublisher;
import com.smartseason.workforce.platform.Cursor;
import com.smartseason.workforce.platform.CursorPage;
import com.smartseason.workforce.platform.PageResponse;
import com.smartseason.workforce.platform.ResourceNotFoundException;
import com.smartseason.workforce.platform.TenantContext;
import com.smartseason.workforce.repo.WorkerContractRepository;
import com.smartseason.workforce.web.dto.WorkerContractCreateRequest;
import com.smartseason.workforce.web.dto.WorkerContractResponse;
import com.smartseason.workforce.web.dto.WorkerContractUpdateRequest;
import com.smartseason.workforce.platform.ListFilter;
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
public class WorkerContractService {

    private static final String RESOURCE = "WorkerContract";
    private static final String ENTITY = "worker_contracts";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("workerId", UUID.class),
            Map.entry("farmId", UUID.class),
            Map.entry("contractType", WorkerContract.ContractType.class),
            Map.entry("pieceUnit", String.class),
            Map.entry("supervisorId", UUID.class),
            Map.entry("status", WorkerContract.Status.class));

    private static final List<String> SEARCHABLE = List.of("pieceUnit");

    private final WorkerContractRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public WorkerContractService(WorkerContractRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<WorkerContractResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<WorkerContract>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(WorkerContractResponse::from));
    }

    public PageResponse<WorkerContractResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(WorkerContractResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<WorkerContractResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<WorkerContract> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(WorkerContractResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public WorkerContractResponse get(UUID id) {
        return WorkerContractResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public WorkerContractResponse create(WorkerContractCreateRequest request) {
        WorkerContract entity = new WorkerContract();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setWorkerId(request.workerId());
        entity.setFarmId(request.farmId());
        entity.setContractType(request.contractType());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setDailyRate(request.dailyRate());
        entity.setPieceRate(request.pieceRate());
        entity.setPieceUnit(request.pieceUnit());
        entity.setSupervisorId(request.supervisorId());
        entity.setStatus(request.status());
        entity.setTerms(request.terms());

        WorkerContract saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("workforce", "WorkerContractCreated", saved.getId(), WorkerContractResponse.from(saved));
        return WorkerContractResponse.from(saved);
    }

    @Transactional
    public WorkerContractResponse update(UUID id, WorkerContractUpdateRequest request) {
        WorkerContract entity = require(id);
        if (request.workerId() != null) {
            entity.setWorkerId(request.workerId());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.contractType() != null) {
            entity.setContractType(request.contractType());
        }
        if (request.startDate() != null) {
            entity.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            entity.setEndDate(request.endDate());
        }
        if (request.dailyRate() != null) {
            entity.setDailyRate(request.dailyRate());
        }
        if (request.pieceRate() != null) {
            entity.setPieceRate(request.pieceRate());
        }
        if (request.pieceUnit() != null) {
            entity.setPieceUnit(request.pieceUnit());
        }
        if (request.supervisorId() != null) {
            entity.setSupervisorId(request.supervisorId());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.terms() != null) {
            entity.setTerms(request.terms());
        }

        WorkerContract saved = repository.save(entity);
        events.publish("workforce", "WorkerContractUpdated", saved.getId(), WorkerContractResponse.from(saved));
        return WorkerContractResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        WorkerContract entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("workforce", "WorkerContractDeleted", id, null);
    }

    private WorkerContract require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
