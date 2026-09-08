package com.smartseason.notification.service;

import com.smartseason.notification.domain.Notification;
import com.smartseason.notification.platform.CountCache;
import com.smartseason.notification.platform.CountCache;
import com.smartseason.notification.platform.EventPublisher;
import com.smartseason.notification.platform.PageResponse;
import com.smartseason.notification.platform.ResourceNotFoundException;
import com.smartseason.notification.platform.TenantContext;
import com.smartseason.notification.repo.NotificationRepository;
import com.smartseason.notification.web.dto.NotificationCreateRequest;
import com.smartseason.notification.web.dto.NotificationResponse;
import com.smartseason.notification.web.dto.NotificationUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final String RESOURCE = "Notification";
    private static final String ENTITY = "notifications";

    private final NotificationRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public NotificationService(NotificationRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<NotificationResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(NotificationResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public NotificationResponse get(UUID id) {
        return NotificationResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public NotificationResponse create(NotificationCreateRequest request) {
        Notification entity = new Notification();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setRecipientUserId(request.recipientUserId());
        entity.setRecipientPhone(request.recipientPhone());
        entity.setRecipientEmail(request.recipientEmail());
        entity.setChannel(request.channel());
        entity.setTemplateCode(request.templateCode());
        entity.setLocale(request.locale());
        entity.setSubject(request.subject());
        entity.setBody(request.body());
        entity.setPayload(request.payload());
        entity.setPriority(request.priority());
        entity.setScheduledFor(request.scheduledFor());
        entity.setSentAt(request.sentAt());
        entity.setDeliveredAt(request.deliveredAt());
        entity.setFailedAt(request.failedAt());
        entity.setFailureReason(request.failureReason());
        entity.setProviderRef(request.providerRef());
        entity.setAttempts(request.attempts());
        entity.setStatus(request.status());
        entity.setIdempotencyKey(request.idempotencyKey());

        Notification saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("platform", "NotificationCreated", saved.getId(), NotificationResponse.from(saved));
        return NotificationResponse.from(saved);
    }

    @Transactional
    public NotificationResponse update(UUID id, NotificationUpdateRequest request) {
        Notification entity = require(id);
        if (request.recipientUserId() != null) {
            entity.setRecipientUserId(request.recipientUserId());
        }
        if (request.recipientPhone() != null) {
            entity.setRecipientPhone(request.recipientPhone());
        }
        if (request.recipientEmail() != null) {
            entity.setRecipientEmail(request.recipientEmail());
        }
        if (request.channel() != null) {
            entity.setChannel(request.channel());
        }
        if (request.templateCode() != null) {
            entity.setTemplateCode(request.templateCode());
        }
        if (request.locale() != null) {
            entity.setLocale(request.locale());
        }
        if (request.subject() != null) {
            entity.setSubject(request.subject());
        }
        if (request.body() != null) {
            entity.setBody(request.body());
        }
        if (request.payload() != null) {
            entity.setPayload(request.payload());
        }
        if (request.priority() != null) {
            entity.setPriority(request.priority());
        }
        if (request.scheduledFor() != null) {
            entity.setScheduledFor(request.scheduledFor());
        }
        if (request.sentAt() != null) {
            entity.setSentAt(request.sentAt());
        }
        if (request.deliveredAt() != null) {
            entity.setDeliveredAt(request.deliveredAt());
        }
        if (request.failedAt() != null) {
            entity.setFailedAt(request.failedAt());
        }
        if (request.failureReason() != null) {
            entity.setFailureReason(request.failureReason());
        }
        if (request.providerRef() != null) {
            entity.setProviderRef(request.providerRef());
        }
        if (request.attempts() != null) {
            entity.setAttempts(request.attempts());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.idempotencyKey() != null) {
            entity.setIdempotencyKey(request.idempotencyKey());
        }

        Notification saved = repository.save(entity);
        events.publish("platform", "NotificationUpdated", saved.getId(), NotificationResponse.from(saved));
        return NotificationResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Notification entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "NotificationDeleted", id, null);
    }

    private Notification require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
