package com.smartseason.payment.service;

import com.smartseason.payment.domain.EscrowHold;
import com.smartseason.payment.platform.CountCache;
import com.smartseason.payment.platform.CountCache;
import com.smartseason.payment.platform.EventPublisher;
import com.smartseason.payment.platform.PageResponse;
import com.smartseason.payment.platform.ResourceNotFoundException;
import com.smartseason.payment.platform.TenantContext;
import com.smartseason.payment.repo.EscrowHoldRepository;
import com.smartseason.payment.web.dto.EscrowHoldCreateRequest;
import com.smartseason.payment.web.dto.EscrowHoldResponse;
import com.smartseason.payment.web.dto.EscrowHoldUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EscrowHoldService {

    private static final String RESOURCE = "EscrowHold";
    private static final String ENTITY = "escrow_holds";

    private final EscrowHoldRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public EscrowHoldService(EscrowHoldRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<EscrowHoldResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(EscrowHoldResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public EscrowHoldResponse get(UUID id) {
        return EscrowHoldResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public EscrowHoldResponse create(EscrowHoldCreateRequest request) {
        EscrowHold entity = new EscrowHold();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setPaymentIntentId(request.paymentIntentId());
        entity.setOrderId(request.orderId());
        entity.setAmount(request.amount());
        entity.setCurrency(request.currency());
        entity.setHeldAt(request.heldAt());
        entity.setReleaseDueAt(request.releaseDueAt());
        entity.setReleasedAt(request.releasedAt());
        entity.setReleasedTo(request.releasedTo());
        entity.setRefundedAt(request.refundedAt());
        entity.setStatus(request.status());
        entity.setReleaseCondition(request.releaseCondition());

        EscrowHold saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("money", "EscrowHoldCreated", saved.getId(), EscrowHoldResponse.from(saved));
        return EscrowHoldResponse.from(saved);
    }

    @Transactional
    public EscrowHoldResponse update(UUID id, EscrowHoldUpdateRequest request) {
        EscrowHold entity = require(id);
        if (request.paymentIntentId() != null) {
            entity.setPaymentIntentId(request.paymentIntentId());
        }
        if (request.orderId() != null) {
            entity.setOrderId(request.orderId());
        }
        if (request.amount() != null) {
            entity.setAmount(request.amount());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.heldAt() != null) {
            entity.setHeldAt(request.heldAt());
        }
        if (request.releaseDueAt() != null) {
            entity.setReleaseDueAt(request.releaseDueAt());
        }
        if (request.releasedAt() != null) {
            entity.setReleasedAt(request.releasedAt());
        }
        if (request.releasedTo() != null) {
            entity.setReleasedTo(request.releasedTo());
        }
        if (request.refundedAt() != null) {
            entity.setRefundedAt(request.refundedAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.releaseCondition() != null) {
            entity.setReleaseCondition(request.releaseCondition());
        }

        EscrowHold saved = repository.save(entity);
        events.publish("money", "EscrowHoldUpdated", saved.getId(), EscrowHoldResponse.from(saved));
        return EscrowHoldResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        EscrowHold entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("money", "EscrowHoldDeleted", id, null);
    }

    private EscrowHold require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
