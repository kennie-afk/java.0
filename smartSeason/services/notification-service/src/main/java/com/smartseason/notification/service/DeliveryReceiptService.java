package com.smartseason.notification.service;

import com.smartseason.notification.domain.DeliveryReceipt;
import com.smartseason.notification.platform.CountCache;
import com.smartseason.notification.platform.CountCache;
import com.smartseason.notification.platform.EventPublisher;
import com.smartseason.notification.platform.PageResponse;
import com.smartseason.notification.platform.ResourceNotFoundException;
import com.smartseason.notification.platform.TenantContext;
import com.smartseason.notification.repo.DeliveryReceiptRepository;
import com.smartseason.notification.web.dto.DeliveryReceiptCreateRequest;
import com.smartseason.notification.web.dto.DeliveryReceiptResponse;
import com.smartseason.notification.web.dto.DeliveryReceiptUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DeliveryReceiptService {

    private static final String RESOURCE = "DeliveryReceipt";
    private static final String ENTITY = "delivery_receipts";

    private final DeliveryReceiptRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public DeliveryReceiptService(DeliveryReceiptRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<DeliveryReceiptResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(DeliveryReceiptResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public DeliveryReceiptResponse get(UUID id) {
        return DeliveryReceiptResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public DeliveryReceiptResponse create(DeliveryReceiptCreateRequest request) {
        DeliveryReceipt entity = new DeliveryReceipt();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setNotificationId(request.notificationId());
        entity.setProvider(request.provider());
        entity.setProviderRef(request.providerRef());
        entity.setStatusCode(request.statusCode());
        entity.setStatusText(request.statusText());
        entity.setReceivedAt(request.receivedAt());
        entity.setRaw(request.raw());

        DeliveryReceipt saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("platform", "DeliveryReceiptCreated", saved.getId(), DeliveryReceiptResponse.from(saved));
        return DeliveryReceiptResponse.from(saved);
    }

    @Transactional
    public DeliveryReceiptResponse update(UUID id, DeliveryReceiptUpdateRequest request) {
        DeliveryReceipt entity = require(id);
        if (request.notificationId() != null) {
            entity.setNotificationId(request.notificationId());
        }
        if (request.provider() != null) {
            entity.setProvider(request.provider());
        }
        if (request.providerRef() != null) {
            entity.setProviderRef(request.providerRef());
        }
        if (request.statusCode() != null) {
            entity.setStatusCode(request.statusCode());
        }
        if (request.statusText() != null) {
            entity.setStatusText(request.statusText());
        }
        if (request.receivedAt() != null) {
            entity.setReceivedAt(request.receivedAt());
        }
        if (request.raw() != null) {
            entity.setRaw(request.raw());
        }

        DeliveryReceipt saved = repository.save(entity);
        events.publish("platform", "DeliveryReceiptUpdated", saved.getId(), DeliveryReceiptResponse.from(saved));
        return DeliveryReceiptResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        DeliveryReceipt entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "DeliveryReceiptDeleted", id, null);
    }

    private DeliveryReceipt require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
