package com.smartseason.payment.service;

import com.smartseason.payment.domain.PaymentIntent;
import com.smartseason.payment.platform.EventPublisher;
import com.smartseason.payment.platform.PageResponse;
import com.smartseason.payment.platform.ResourceNotFoundException;
import com.smartseason.payment.platform.TenantContext;
import com.smartseason.payment.repo.PaymentIntentRepository;
import com.smartseason.payment.web.dto.PaymentIntentCreateRequest;
import com.smartseason.payment.web.dto.PaymentIntentResponse;
import com.smartseason.payment.web.dto.PaymentIntentUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentIntentService {

    private static final String RESOURCE = "PaymentIntent";

    private final PaymentIntentRepository repository;
    private final EventPublisher events;

    public PaymentIntentService(PaymentIntentRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<PaymentIntentResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(PaymentIntentResponse::from));
    }

    public PaymentIntentResponse get(UUID id) {
        return PaymentIntentResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public PaymentIntentResponse create(PaymentIntentCreateRequest request) {
        PaymentIntent entity = new PaymentIntent();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setReference(request.reference());
        entity.setOrderId(request.orderId());
        entity.setPayerOrgId(request.payerOrgId());
        entity.setPayeeOrgId(request.payeeOrgId());
        entity.setAmount(request.amount());
        entity.setCurrency(request.currency());
        entity.setMethod(request.method());
        entity.setPurpose(request.purpose());
        entity.setPayerPhone(request.payerPhone());
        entity.setStatus(request.status());
        entity.setIdempotencyKey(request.idempotencyKey());
        entity.setInitiatedAt(request.initiatedAt());
        entity.setCompletedAt(request.completedAt());
        entity.setFailureReason(request.failureReason());
        entity.setProviderRef(request.providerRef());
        entity.setEscrow(request.escrow());

        PaymentIntent saved = repository.save(entity);
        events.publish("money", "PaymentIntentCreated", saved.getId(), PaymentIntentResponse.from(saved));
        return PaymentIntentResponse.from(saved);
    }

    @Transactional
    public PaymentIntentResponse update(UUID id, PaymentIntentUpdateRequest request) {
        PaymentIntent entity = require(id);
        if (request.reference() != null) {
            entity.setReference(request.reference());
        }
        if (request.orderId() != null) {
            entity.setOrderId(request.orderId());
        }
        if (request.payerOrgId() != null) {
            entity.setPayerOrgId(request.payerOrgId());
        }
        if (request.payeeOrgId() != null) {
            entity.setPayeeOrgId(request.payeeOrgId());
        }
        if (request.amount() != null) {
            entity.setAmount(request.amount());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.method() != null) {
            entity.setMethod(request.method());
        }
        if (request.purpose() != null) {
            entity.setPurpose(request.purpose());
        }
        if (request.payerPhone() != null) {
            entity.setPayerPhone(request.payerPhone());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.idempotencyKey() != null) {
            entity.setIdempotencyKey(request.idempotencyKey());
        }
        if (request.initiatedAt() != null) {
            entity.setInitiatedAt(request.initiatedAt());
        }
        if (request.completedAt() != null) {
            entity.setCompletedAt(request.completedAt());
        }
        if (request.failureReason() != null) {
            entity.setFailureReason(request.failureReason());
        }
        if (request.providerRef() != null) {
            entity.setProviderRef(request.providerRef());
        }
        if (request.escrow() != null) {
            entity.setEscrow(request.escrow());
        }

        PaymentIntent saved = repository.save(entity);
        events.publish("money", "PaymentIntentUpdated", saved.getId(), PaymentIntentResponse.from(saved));
        return PaymentIntentResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        PaymentIntent entity = require(id);
        repository.delete(entity);
        events.publish("money", "PaymentIntentDeleted", id, null);
    }

    private PaymentIntent require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
