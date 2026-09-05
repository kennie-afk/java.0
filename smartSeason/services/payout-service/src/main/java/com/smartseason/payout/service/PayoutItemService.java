package com.smartseason.payout.service;

import com.smartseason.payout.domain.PayoutItem;
import com.smartseason.payout.platform.EventPublisher;
import com.smartseason.payout.platform.PageResponse;
import com.smartseason.payout.platform.ResourceNotFoundException;
import com.smartseason.payout.platform.TenantContext;
import com.smartseason.payout.repo.PayoutItemRepository;
import com.smartseason.payout.web.dto.PayoutItemCreateRequest;
import com.smartseason.payout.web.dto.PayoutItemResponse;
import com.smartseason.payout.web.dto.PayoutItemUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PayoutItemService {

    private static final String RESOURCE = "PayoutItem";

    private final PayoutItemRepository repository;
    private final EventPublisher events;

    public PayoutItemService(PayoutItemRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<PayoutItemResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(PayoutItemResponse::from));
    }

    public PayoutItemResponse get(UUID id) {
        return PayoutItemResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public PayoutItemResponse create(PayoutItemCreateRequest request) {
        PayoutItem entity = new PayoutItem();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setBatchId(request.batchId());
        entity.setSettlementId(request.settlementId());
        entity.setPayeeType(request.payeeType());
        entity.setPayeeId(request.payeeId());
        entity.setPayeeName(request.payeeName());
        entity.setPayeePhone(request.payeePhone());
        entity.setAmount(request.amount());
        entity.setCurrency(request.currency());
        entity.setPaymentIntentId(request.paymentIntentId());
        entity.setStatus(request.status());
        entity.setFailureReason(request.failureReason());
        entity.setSentAt(request.sentAt());
        entity.setPaidAt(request.paidAt());
        entity.setIdempotencyKey(request.idempotencyKey());

        PayoutItem saved = repository.save(entity);
        events.publish("money", "PayoutItemCreated", saved.getId(), PayoutItemResponse.from(saved));
        return PayoutItemResponse.from(saved);
    }

    @Transactional
    public PayoutItemResponse update(UUID id, PayoutItemUpdateRequest request) {
        PayoutItem entity = require(id);
        if (request.batchId() != null) {
            entity.setBatchId(request.batchId());
        }
        if (request.settlementId() != null) {
            entity.setSettlementId(request.settlementId());
        }
        if (request.payeeType() != null) {
            entity.setPayeeType(request.payeeType());
        }
        if (request.payeeId() != null) {
            entity.setPayeeId(request.payeeId());
        }
        if (request.payeeName() != null) {
            entity.setPayeeName(request.payeeName());
        }
        if (request.payeePhone() != null) {
            entity.setPayeePhone(request.payeePhone());
        }
        if (request.amount() != null) {
            entity.setAmount(request.amount());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.paymentIntentId() != null) {
            entity.setPaymentIntentId(request.paymentIntentId());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.failureReason() != null) {
            entity.setFailureReason(request.failureReason());
        }
        if (request.sentAt() != null) {
            entity.setSentAt(request.sentAt());
        }
        if (request.paidAt() != null) {
            entity.setPaidAt(request.paidAt());
        }
        if (request.idempotencyKey() != null) {
            entity.setIdempotencyKey(request.idempotencyKey());
        }

        PayoutItem saved = repository.save(entity);
        events.publish("money", "PayoutItemUpdated", saved.getId(), PayoutItemResponse.from(saved));
        return PayoutItemResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        PayoutItem entity = require(id);
        repository.delete(entity);
        events.publish("money", "PayoutItemDeleted", id, null);
    }

    private PayoutItem require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
