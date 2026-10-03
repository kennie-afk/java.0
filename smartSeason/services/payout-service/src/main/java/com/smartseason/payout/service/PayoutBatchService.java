package com.smartseason.payout.service;

import com.smartseason.payout.domain.PayoutBatch;
import com.smartseason.payout.platform.CountCache;
import com.smartseason.payout.platform.CountCache;
import com.smartseason.payout.platform.EventPublisher;
import com.smartseason.payout.platform.ReferenceChecker;
import com.smartseason.payout.platform.Cursor;
import com.smartseason.payout.platform.CursorPage;
import com.smartseason.payout.platform.PageResponse;
import com.smartseason.payout.platform.ResourceNotFoundException;
import com.smartseason.payout.platform.TenantContext;
import com.smartseason.payout.repo.PayoutBatchRepository;
import com.smartseason.payout.web.dto.PayoutBatchCreateRequest;
import com.smartseason.payout.web.dto.PayoutBatchResponse;
import com.smartseason.payout.web.dto.PayoutBatchUpdateRequest;
import com.smartseason.payout.platform.ListFilter;
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
public class PayoutBatchService {

    private static final String RESOURCE = "PayoutBatch";
    private static final String ENTITY = "payout_batches";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("batchNumber", String.class),
            Map.entry("farmId", UUID.class),
            Map.entry("payoutType", PayoutBatch.PayoutType.class),
            Map.entry("currency", String.class),
            Map.entry("createdBy", UUID.class),
            Map.entry("status", PayoutBatch.Status.class));

    private static final List<String> SEARCHABLE = List.of("batchNumber", "currency");

    private final PayoutBatchRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public PayoutBatchService(PayoutBatchRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<PayoutBatchResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<PayoutBatch>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(PayoutBatchResponse::from));
    }

    public PageResponse<PayoutBatchResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(PayoutBatchResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<PayoutBatchResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<PayoutBatch> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(PayoutBatchResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public PayoutBatchResponse get(UUID id) {
        return PayoutBatchResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public PayoutBatchResponse create(PayoutBatchCreateRequest request) {
        PayoutBatch entity = new PayoutBatch();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setBatchNumber(request.batchNumber());
        entity.setFarmId(request.farmId());
        entity.setPayoutType(request.payoutType());
        entity.setItemCount(request.itemCount());
        entity.setTotalAmount(request.totalAmount());
        entity.setCurrency(request.currency());
        entity.setScheduledFor(request.scheduledFor());
        entity.setSubmittedAt(request.submittedAt());
        entity.setCompletedAt(request.completedAt());
        entity.setCreatedBy(request.createdBy());
        entity.setStatus(request.status());

        PayoutBatch saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("money", "PayoutBatchCreated", saved.getId(), PayoutBatchResponse.from(saved));
        return PayoutBatchResponse.from(saved);
    }

    @Transactional
    public PayoutBatchResponse update(UUID id, PayoutBatchUpdateRequest request) {
        PayoutBatch entity = require(id);
        if (request.batchNumber() != null) {
            entity.setBatchNumber(request.batchNumber());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.payoutType() != null) {
            entity.setPayoutType(request.payoutType());
        }
        if (request.itemCount() != null) {
            entity.setItemCount(request.itemCount());
        }
        if (request.totalAmount() != null) {
            entity.setTotalAmount(request.totalAmount());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.scheduledFor() != null) {
            entity.setScheduledFor(request.scheduledFor());
        }
        if (request.submittedAt() != null) {
            entity.setSubmittedAt(request.submittedAt());
        }
        if (request.completedAt() != null) {
            entity.setCompletedAt(request.completedAt());
        }
        if (request.createdBy() != null) {
            entity.setCreatedBy(request.createdBy());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        PayoutBatch saved = repository.save(entity);
        events.publish("money", "PayoutBatchUpdated", saved.getId(), PayoutBatchResponse.from(saved));
        return PayoutBatchResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        PayoutBatch entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("money", "PayoutBatchDeleted", id, null);
    }

    private PayoutBatch require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
