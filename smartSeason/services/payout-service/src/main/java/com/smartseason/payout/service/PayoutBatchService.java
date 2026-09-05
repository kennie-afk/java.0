package com.smartseason.payout.service;

import com.smartseason.payout.domain.PayoutBatch;
import com.smartseason.payout.platform.EventPublisher;
import com.smartseason.payout.platform.PageResponse;
import com.smartseason.payout.platform.ResourceNotFoundException;
import com.smartseason.payout.platform.TenantContext;
import com.smartseason.payout.repo.PayoutBatchRepository;
import com.smartseason.payout.web.dto.PayoutBatchCreateRequest;
import com.smartseason.payout.web.dto.PayoutBatchResponse;
import com.smartseason.payout.web.dto.PayoutBatchUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PayoutBatchService {

    private static final String RESOURCE = "PayoutBatch";

    private final PayoutBatchRepository repository;
    private final EventPublisher events;

    public PayoutBatchService(PayoutBatchRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<PayoutBatchResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(PayoutBatchResponse::from));
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
        events.publish("money", "PayoutBatchDeleted", id, null);
    }

    private PayoutBatch require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
