package com.smartseason.payment.service;

import com.smartseason.payment.domain.ProviderCallback;
import com.smartseason.payment.platform.EventPublisher;
import com.smartseason.payment.platform.PageResponse;
import com.smartseason.payment.platform.ResourceNotFoundException;
import com.smartseason.payment.platform.TenantContext;
import com.smartseason.payment.repo.ProviderCallbackRepository;
import com.smartseason.payment.web.dto.ProviderCallbackCreateRequest;
import com.smartseason.payment.web.dto.ProviderCallbackResponse;
import com.smartseason.payment.web.dto.ProviderCallbackUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProviderCallbackService {

    private static final String RESOURCE = "ProviderCallback";

    private final ProviderCallbackRepository repository;
    private final EventPublisher events;

    public ProviderCallbackService(ProviderCallbackRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<ProviderCallbackResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(ProviderCallbackResponse::from));
    }

    public ProviderCallbackResponse get(UUID id) {
        return ProviderCallbackResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ProviderCallbackResponse create(ProviderCallbackCreateRequest request) {
        ProviderCallback entity = new ProviderCallback();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setProvider(request.provider());
        entity.setCallbackType(request.callbackType());
        entity.setExternalRef(request.externalRef());
        entity.setSignature(request.signature());
        entity.setPayload(request.payload());
        entity.setReceivedAt(request.receivedAt());
        entity.setProcessedAt(request.processedAt());
        entity.setStatus(request.status());
        entity.setError(request.error());

        ProviderCallback saved = repository.save(entity);
        events.publish("money", "ProviderCallbackCreated", saved.getId(), ProviderCallbackResponse.from(saved));
        return ProviderCallbackResponse.from(saved);
    }

    @Transactional
    public ProviderCallbackResponse update(UUID id, ProviderCallbackUpdateRequest request) {
        ProviderCallback entity = require(id);
        if (request.provider() != null) {
            entity.setProvider(request.provider());
        }
        if (request.callbackType() != null) {
            entity.setCallbackType(request.callbackType());
        }
        if (request.externalRef() != null) {
            entity.setExternalRef(request.externalRef());
        }
        if (request.signature() != null) {
            entity.setSignature(request.signature());
        }
        if (request.payload() != null) {
            entity.setPayload(request.payload());
        }
        if (request.receivedAt() != null) {
            entity.setReceivedAt(request.receivedAt());
        }
        if (request.processedAt() != null) {
            entity.setProcessedAt(request.processedAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.error() != null) {
            entity.setError(request.error());
        }

        ProviderCallback saved = repository.save(entity);
        events.publish("money", "ProviderCallbackUpdated", saved.getId(), ProviderCallbackResponse.from(saved));
        return ProviderCallbackResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        ProviderCallback entity = require(id);
        repository.delete(entity);
        events.publish("money", "ProviderCallbackDeleted", id, null);
    }

    private ProviderCallback require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
