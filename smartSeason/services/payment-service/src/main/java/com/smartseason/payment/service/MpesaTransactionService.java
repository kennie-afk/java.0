package com.smartseason.payment.service;

import com.smartseason.payment.domain.MpesaTransaction;
import com.smartseason.payment.platform.CountCache;
import com.smartseason.payment.platform.CountCache;
import com.smartseason.payment.platform.EventPublisher;
import com.smartseason.payment.platform.PageResponse;
import com.smartseason.payment.platform.ResourceNotFoundException;
import com.smartseason.payment.platform.TenantContext;
import com.smartseason.payment.repo.MpesaTransactionRepository;
import com.smartseason.payment.web.dto.MpesaTransactionCreateRequest;
import com.smartseason.payment.web.dto.MpesaTransactionResponse;
import com.smartseason.payment.web.dto.MpesaTransactionUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MpesaTransactionService {

    private static final String RESOURCE = "MpesaTransaction";
    private static final String ENTITY = "mpesa_transactions";

    private final MpesaTransactionRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public MpesaTransactionService(MpesaTransactionRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<MpesaTransactionResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(MpesaTransactionResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public MpesaTransactionResponse get(UUID id) {
        return MpesaTransactionResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public MpesaTransactionResponse create(MpesaTransactionCreateRequest request) {
        MpesaTransaction entity = new MpesaTransaction();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setPaymentIntentId(request.paymentIntentId());
        entity.setMerchantRequestId(request.merchantRequestId());
        entity.setCheckoutRequestId(request.checkoutRequestId());
        entity.setMpesaReceiptNumber(request.mpesaReceiptNumber());
        entity.setPhoneNumber(request.phoneNumber());
        entity.setAmount(request.amount());
        entity.setTransactionType(request.transactionType());
        entity.setResultCode(request.resultCode());
        entity.setResultDesc(request.resultDesc());
        entity.setTransactionDate(request.transactionDate());
        entity.setAccountReference(request.accountReference());
        entity.setRawCallback(request.rawCallback());
        entity.setStatus(request.status());

        MpesaTransaction saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("money", "MpesaTransactionCreated", saved.getId(), MpesaTransactionResponse.from(saved));
        return MpesaTransactionResponse.from(saved);
    }

    @Transactional
    public MpesaTransactionResponse update(UUID id, MpesaTransactionUpdateRequest request) {
        MpesaTransaction entity = require(id);
        if (request.paymentIntentId() != null) {
            entity.setPaymentIntentId(request.paymentIntentId());
        }
        if (request.merchantRequestId() != null) {
            entity.setMerchantRequestId(request.merchantRequestId());
        }
        if (request.checkoutRequestId() != null) {
            entity.setCheckoutRequestId(request.checkoutRequestId());
        }
        if (request.mpesaReceiptNumber() != null) {
            entity.setMpesaReceiptNumber(request.mpesaReceiptNumber());
        }
        if (request.phoneNumber() != null) {
            entity.setPhoneNumber(request.phoneNumber());
        }
        if (request.amount() != null) {
            entity.setAmount(request.amount());
        }
        if (request.transactionType() != null) {
            entity.setTransactionType(request.transactionType());
        }
        if (request.resultCode() != null) {
            entity.setResultCode(request.resultCode());
        }
        if (request.resultDesc() != null) {
            entity.setResultDesc(request.resultDesc());
        }
        if (request.transactionDate() != null) {
            entity.setTransactionDate(request.transactionDate());
        }
        if (request.accountReference() != null) {
            entity.setAccountReference(request.accountReference());
        }
        if (request.rawCallback() != null) {
            entity.setRawCallback(request.rawCallback());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        MpesaTransaction saved = repository.save(entity);
        events.publish("money", "MpesaTransactionUpdated", saved.getId(), MpesaTransactionResponse.from(saved));
        return MpesaTransactionResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        MpesaTransaction entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("money", "MpesaTransactionDeleted", id, null);
    }

    private MpesaTransaction require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
