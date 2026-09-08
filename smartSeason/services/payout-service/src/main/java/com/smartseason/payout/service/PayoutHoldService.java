package com.smartseason.payout.service;

import com.smartseason.payout.domain.PayoutHold;
import com.smartseason.payout.platform.CountCache;
import com.smartseason.payout.platform.CountCache;
import com.smartseason.payout.platform.EventPublisher;
import com.smartseason.payout.platform.PageResponse;
import com.smartseason.payout.platform.ResourceNotFoundException;
import com.smartseason.payout.platform.TenantContext;
import com.smartseason.payout.repo.PayoutHoldRepository;
import com.smartseason.payout.web.dto.PayoutHoldCreateRequest;
import com.smartseason.payout.web.dto.PayoutHoldResponse;
import com.smartseason.payout.web.dto.PayoutHoldUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PayoutHoldService {

    private static final String RESOURCE = "PayoutHold";
    private static final String ENTITY = "payout_holds";

    private final PayoutHoldRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public PayoutHoldService(PayoutHoldRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<PayoutHoldResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(PayoutHoldResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public PayoutHoldResponse get(UUID id) {
        return PayoutHoldResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public PayoutHoldResponse create(PayoutHoldCreateRequest request) {
        PayoutHold entity = new PayoutHold();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setPayoutItemId(request.payoutItemId());
        entity.setPayeeId(request.payeeId());
        entity.setReason(request.reason());
        entity.setFraudCaseId(request.fraudCaseId());
        entity.setAmount(request.amount());
        entity.setHeldAt(request.heldAt());
        entity.setHeldBy(request.heldBy());
        entity.setReleasedAt(request.releasedAt());
        entity.setReleasedBy(request.releasedBy());
        entity.setStatus(request.status());
        entity.setNotes(request.notes());

        PayoutHold saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("money", "PayoutHoldCreated", saved.getId(), PayoutHoldResponse.from(saved));
        return PayoutHoldResponse.from(saved);
    }

    @Transactional
    public PayoutHoldResponse update(UUID id, PayoutHoldUpdateRequest request) {
        PayoutHold entity = require(id);
        if (request.payoutItemId() != null) {
            entity.setPayoutItemId(request.payoutItemId());
        }
        if (request.payeeId() != null) {
            entity.setPayeeId(request.payeeId());
        }
        if (request.reason() != null) {
            entity.setReason(request.reason());
        }
        if (request.fraudCaseId() != null) {
            entity.setFraudCaseId(request.fraudCaseId());
        }
        if (request.amount() != null) {
            entity.setAmount(request.amount());
        }
        if (request.heldAt() != null) {
            entity.setHeldAt(request.heldAt());
        }
        if (request.heldBy() != null) {
            entity.setHeldBy(request.heldBy());
        }
        if (request.releasedAt() != null) {
            entity.setReleasedAt(request.releasedAt());
        }
        if (request.releasedBy() != null) {
            entity.setReleasedBy(request.releasedBy());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }

        PayoutHold saved = repository.save(entity);
        events.publish("money", "PayoutHoldUpdated", saved.getId(), PayoutHoldResponse.from(saved));
        return PayoutHoldResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        PayoutHold entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("money", "PayoutHoldDeleted", id, null);
    }

    private PayoutHold require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
